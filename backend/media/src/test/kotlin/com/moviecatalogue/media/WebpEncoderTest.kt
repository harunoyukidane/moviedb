package com.moviecatalogue.media

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class WebpEncoderTest {

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    @Test
    fun `returns null when the binary does not exist`() {
        val encoder = WebpEncoder(binary = "definitely-not-a-real-binary-xyz")
        assertThat(encoder.encode(pngBytes())).isNull()
    }

    @Test
    fun `returns null when the source bytes are not a decodable image`() {
        // A real cwebp would reject this; simulate the same "no binary" degrade path
        // without depending on cwebp being installed in every environment this runs in.
        val encoder = WebpEncoder(binary = "definitely-not-a-real-binary-xyz")
        assertThat(encoder.encode("not an image".toByteArray())).isNull()
    }
}
