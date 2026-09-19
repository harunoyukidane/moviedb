package com.moviecatalogue.media

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageContentValidatorTest {

    private val validator = ImageContentValidator()

    private fun realImage(format: String, w: Int = 4, h: Int = 3): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        check(ImageIO.write(img, format, out)) { "no writer for $format" }
        return out.toByteArray()
    }

    @Test
    fun `accepts a real JPEG and reports dimensions`() {
        val v = validator.validate(realImage("jpg"))
        assertThat(v.format).isEqualTo(ImageFormat.JPEG)
        assertThat(v.width).isEqualTo(4)
        assertThat(v.height).isEqualTo(3)
    }

    @Test
    fun `accepts a real PNG`() {
        val v = validator.validate(realImage("png"))
        assertThat(v.format).isEqualTo(ImageFormat.PNG)
    }

    @Test
    fun `downscales an oversized JPEG to the max dimension, preserving aspect ratio`() {
        val small = ImageContentValidator(maxDimension = 100)
        val v = small.validate(realImage("jpg", w = 400, h = 200))
        assertThat(v.format).isEqualTo(ImageFormat.JPEG)
        assertThat(v.width).isEqualTo(100)
        assertThat(v.height).isEqualTo(50)
        assertThat(v.byteSize).isLessThan(realImage("jpg", w = 400, h = 200).size.toLong())
    }

    @Test
    fun `downscales an oversized PNG and keeps it PNG`() {
        val small = ImageContentValidator(maxDimension = 100)
        val v = small.validate(realImage("png", w = 200, h = 400))
        assertThat(v.format).isEqualTo(ImageFormat.PNG)
        assertThat(v.width).isEqualTo(50)
        assertThat(v.height).isEqualTo(100)
    }

    @Test
    fun `leaves an image already within the max dimension untouched`() {
        val small = ImageContentValidator(maxDimension = 100)
        val original = realImage("jpg", w = 80, h = 60)
        val v = small.validate(original)
        assertThat(v.width).isEqualTo(80)
        assertThat(v.height).isEqualTo(60)
        assertThat(v.bytes).isEqualTo(original)
    }

    @Test
    fun `rejects empty upload`() {
        assertThatThrownBy { validator.validate(ByteArray(0)) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }

    @Test
    fun `rejects oversized upload with PAYLOAD_TOO_LARGE`() {
        val small = ImageContentValidator(maxBytes = 10)
        // 11 bytes with a JPEG-ish header still trips the size gate first
        val bytes = ByteArray(11) { 0xFF.toByte() }
        assertThatThrownBy { small.validate(bytes) }
            .isInstanceOf(PayloadTooLargeException::class.java)
    }

    @Test
    fun `rejects spoofed content - text with jpg intent`() {
        // "this is not an image" — no valid signature
        val bytes = "this is definitely not an image file at all!!".toByteArray()
        assertThatThrownBy { validator.validate(bytes) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }

    @Test
    fun `rejects truncated JPEG (valid signature, undecodable)`() {
        val full = realImage("jpg")
        // keep the signature bytes but cut the file so decode fails
        val truncated = full.copyOfRange(0, minOf(full.size, 8))
        assertThatThrownBy { validator.validate(truncated) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }

    @Test
    fun `rejects GIF (not in allowlist)`() {
        // GIF89a signature
        val gif = byteArrayOf(
            'G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(),
            '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte(),
        ) + ByteArray(64)
        assertThatThrownBy { validator.validate(gif) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }

    @Test
    fun `rejects SVG (text-based, no binary image signature)`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""".toByteArray()
        assertThatThrownBy { validator.validate(svg) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }

    @Test
    fun `rejects a PNG signature followed by garbage (undecodable)`() {
        val fakePng = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ) + ByteArray(64) { 0x7F }
        assertThatThrownBy { validator.validate(fakePng) }
            .isInstanceOf(UnsupportedMediaTypeException::class.java)
    }
}
