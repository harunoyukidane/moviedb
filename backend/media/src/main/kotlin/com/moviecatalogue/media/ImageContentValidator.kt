package com.moviecatalogue.media

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

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
        return ValidatedImage(bytes, format, image.width, image.height)
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
