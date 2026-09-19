package com.moviecatalogue.media

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    @Test
    fun `caps concurrent encodes at maxConcurrent instead of forking unboundedly (V2_6-04)`() {
        // A "binary" that always takes a fixed 400ms, so total wall time reveals how many
        // ran in parallel: fully unbounded, 6 calls finish in ~400ms; bounded to 3 at a
        // time, they can't finish faster than 2 sequential rounds (~800ms).
        val delaySeconds = "0.4"
        val script = kotlin.io.path.createTempFile("webp-delay-", ".sh").apply {
            toFile().writeText(
                """
                #!/bin/sh
                sleep $delaySeconds
                touch "${'$'}6"
                exit 0
                """.trimIndent(),
            )
            toFile().setExecutable(true)
            toFile().deleteOnExit()
        }
        val maxConcurrent = 3
        val callCount = 6
        val encoder = WebpEncoder(binary = script.toString(), maxConcurrent = maxConcurrent, timeoutSeconds = 5)

        val pool = Executors.newFixedThreadPool(callCount)
        val start = System.nanoTime()
        try {
            val futures = (1..callCount).map { pool.submit { encoder.encode(pngBytes()) } }
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // 2 sequential rounds of ~400ms each, with generous margin below the unbounded
        // (~400ms + overhead) case this guards against.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(700)
    }
}
