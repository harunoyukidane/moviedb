package com.moviecatalogue.media

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class WebpEncoderTest {

    private fun pngBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    /** A stand-in `cwebp` plus how long each invocation of it actually takes. */
    private data class FakeBinary(val path: String, val perCallDelayMs: Long)

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /**
     * Builds a stand-in for `cwebp` that takes a predictable amount of time and then
     * writes the output file, so a timing assertion can reveal how many invocations
     * really ran in parallel. Argument 6 is the output path - `WebpEncoder` invokes
     * `<binary> -quiet -q <n> <input> -o <output>`.
     *
     * It has to be a natively executable script for the host OS. `ProcessBuilder`
     * calls straight into `CreateProcess` on Windows, which cannot run a `#!/bin/sh`
     * file at all - it fails with `CreateProcess error=193, %1 is not a valid Win32
     * application`. `WebpEncoder` catches that `IOException` and degrades to "no
     * variant" in milliseconds, so a shell script here would make every concurrency
     * assertion below measure nothing instead of failing honestly. Hence `.cmd` on
     * Windows and `.sh` everywhere else.
     *
     * The script writes a byte (rather than creating an empty file) so `encode`
     * returns non-null on success: the tests assert that, which is what proves the
     * fake binary genuinely executed.
     */
    private fun fakeDelayingBinary(roughDelayMs: Long): FakeBinary {
        if (isWindows) {
            // `cmd` has no sub-second sleep. `ping -n N 127.0.0.1` waits ~(N-1) seconds
            // on loopback and needs no working network, so round up to whole seconds.
            val seconds = maxOf(1L, (roughDelayMs + 999) / 1000)
            val file = kotlin.io.path.createTempFile("webp-delay-", ".cmd")
            file.toFile().writeText(
                """
                @echo off
                ping -n ${seconds + 1} 127.0.0.1 >nul
                echo x>"%~6"
                exit /b 0
                """.trimIndent(),
            )
            file.toFile().deleteOnExit()
            return FakeBinary(file.toString(), seconds * 1000L)
        }
        val file = kotlin.io.path.createTempFile("webp-delay-", ".sh")
        file.toFile().writeText(
            """
            #!/bin/sh
            sleep ${roughDelayMs / 1000.0}
            printf x > "${'$'}6"
            exit 0
            """.trimIndent(),
        )
        file.toFile().setExecutable(true)
        file.toFile().deleteOnExit()
        return FakeBinary(file.toString(), roughDelayMs)
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
        // Total wall time reveals how many ran in parallel: fully unbounded, all 6 calls
        // finish in about one delay; bounded to 3 at a time, they cannot finish in fewer
        // than 2 sequential rounds.
        val maxConcurrent = 3
        val callCount = 6
        val fake = fakeDelayingBinary(roughDelayMs = 400)
        val encoder = WebpEncoder(
            binary = fake.path,
            maxConcurrent = maxConcurrent,
            timeoutSeconds = 30,
            permitWaitSeconds = 30,
        )

        val pool = Executors.newFixedThreadPool(callCount)
        val start = System.nanoTime()
        val results = try {
            pool.invokeAll((1..callCount).map { Callable { encoder.encode(pngBytes()) } })
                .map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // Guards the assertion below: if the fake binary could not be executed at all,
        // every encode degrades to null in milliseconds and the timing check would pass
        // or fail for reasons that have nothing to do with the concurrency cap.
        assertThat(results).describedAs("every encode ran the fake binary").doesNotContainNull()

        val rounds = callCount / maxConcurrent
        val minExpectedMs = (fake.perCallDelayMs * rounds * 85) / 100
        assertThat(elapsedMs)
            .describedAs("%d calls capped at %d should take ~%d rounds", callCount, maxConcurrent, rounds)
            .isGreaterThanOrEqualTo(minExpectedMs)
    }

    @Test
    fun `a caller that cannot get a permit within the wait budget returns null promptly (V2_7-03)`() {
        // The only permit is held by a slow encode for well beyond the contender's wait
        // budget, so the contender must degrade rather than queue behind it.
        val fake = fakeDelayingBinary(roughDelayMs = 2000)
        val encoder = WebpEncoder(
            binary = fake.path,
            maxConcurrent = 1,
            timeoutSeconds = 30,
            permitWaitSeconds = 1,
        )

        val pool = Executors.newFixedThreadPool(2)
        try {
            val holder = pool.submit(Callable { encoder.encode(pngBytes()) })
            Thread.sleep(300) // let the holder take the only permit first
            val start = System.nanoTime()
            val contender = pool.submit(Callable { encoder.encode(pngBytes()) }).get(60, TimeUnit.SECONDS)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            assertThat(contender).isNull()
            assertThat(elapsedMs)
                .describedAs("gave up after the wait budget instead of waiting out the holder")
                .isLessThan(fake.perCallDelayMs)
            // Without this the contender's null is meaningless - it would also be null if
            // the fake binary never ran and the permit was free the whole time.
            assertThat(holder.get(60, TimeUnit.SECONDS))
                .describedAs("the holder really did occupy the only permit")
                .isNotNull()
        } finally {
            pool.shutdownNow()
        }
    }
}
