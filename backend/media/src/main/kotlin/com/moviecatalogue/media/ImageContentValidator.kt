package com.moviecatalogue.media

import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** An allowed image format with its canonical media type and file extension. */
enum class ImageFormat(val mediaType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
}

/** Result of successful validation: the detected format and decoded dimensions. */
data class ValidatedImage(
    val bytes: ByteArray,
    val format: ImageFormat,
    val width: Int,
    val height: Int,
) {
    val byteSize: Long get() = bytes.size.toLong()
}

/**
 * Validates artwork by content, never by filename or client Content-Type (§10):
 * 1. size gate (bytes already bounded by the caller's streaming limit),
 * 2. magic-byte signature check restricted to JPEG/PNG/WebP,
 * 3. full decode with an image decoder to reject truncated/spoofed files.
 * GIF, SVG, and executables fail the signature and/or decode checks.
 */
class ImageContentValidator(
    val maxBytes: Long = 5L * 1024 * 1024, // 5 MiB (§10)
    // Nothing in the app displays artwork/photos wider than ~280px of CSS width;
    // cap the longest edge well above that (2x for retina) so a full-resolution
    // upload is never stored or served for what is ultimately a poster-sized slot.
    private val maxDimension: Int = 640,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun validate(bytes: ByteArray): ValidatedImage {
        if (bytes.isEmpty()) throw UnsupportedMediaTypeException("empty upload")
        if (bytes.size.toLong() > maxBytes) throw PayloadTooLargeException()

        val format = detectSignature(bytes)
            ?: throw UnsupportedMediaTypeException("unrecognized or disallowed image signature")

        // Full decode: a spoofed extension or truncated file fails here.
        val image = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            log.debug("image decode failed: {}", e.message)
            null
        } ?: throw UnsupportedMediaTypeException("content is not a decodable image")

        if (image.width <= 0 || image.height <= 0) {
            throw UnsupportedMediaTypeException("image has no dimensions")
        }

        if (image.width <= maxDimension && image.height <= maxDimension) {
            return ValidatedImage(bytes, format, image.width, image.height)
        }
        return downscale(image, format)
    }

    /**
     * Shrinks an oversized upload so its longest edge is [maxDimension], preserving
     * aspect ratio. WebP has no ImageIO writer available here (the TwelveMonkeys
     * plugin only reads it), so a downscaled WebP is re-encoded as JPEG rather than
     * round-tripping the original format.
     */
    private fun downscale(image: BufferedImage, format: ImageFormat): ValidatedImage {
        val scale = maxDimension.toDouble() / maxOf(image.width, image.height)
        val newWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (image.height * scale).toInt().coerceAtLeast(1)

        val outputFormat = if (format == ImageFormat.WEBP) ImageFormat.JPEG else format
        val opaque = outputFormat == ImageFormat.JPEG
        val resized = BufferedImage(newWidth, newHeight, if (opaque) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
        val g = resized.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            if (opaque) {
                // JPEG has no alpha channel; flatten onto white first (matches the
                // opaque poster/photo backgrounds this app actually uses).
                g.color = Color.WHITE
                g.fillRect(0, 0, newWidth, newHeight)
            }
            g.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
        } finally {
            g.dispose()
        }

        val out = ByteArrayOutputStream()
        if (outputFormat == ImageFormat.JPEG) {
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f
            }
            writer.output = ImageIO.createImageOutputStream(out)
            writer.write(null, IIOImage(resized, null, null), param)
            writer.dispose()
        } else {
            check(ImageIO.write(resized, "png", out)) { "no writer for png" }
        }

        return ValidatedImage(out.toByteArray(), outputFormat, newWidth, newHeight)
    }

    /** Magic-byte signature detection limited to the three allowed formats. */
    private fun detectSignature(b: ByteArray): ImageFormat? {
        if (b.size < 12) return null
        // JPEG: FF D8 FF
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return ImageFormat.JPEG
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte() &&
            b[4] == 0x0D.toByte() && b[5] == 0x0A.toByte() && b[6] == 0x1A.toByte() && b[7] == 0x0A.toByte()
        ) return ImageFormat.PNG
        // WebP: "RIFF" .... "WEBP"
        if (b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() && b[10] == 'B'.code.toByte() && b[11] == 'P'.code.toByte()
        ) return ImageFormat.WEBP
        return null
    }
}
