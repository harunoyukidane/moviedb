package com.moviecatalogue.media

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Best-effort WebP transcoder shelling out to the `cwebp` CLI (libwebp) rather
 * than a JNI/native-binding dependency: `cwebp` is a single, widely-packaged
 * binary (`apk add libwebp-tools` / `apt install webp`) with no per-platform jar
 * to manage, unlike embedding an encoder in the JVM itself (ImageIO has no WebP
 * writer; the `imageio-webp` plugin already in this module only reads WebP).
 *
 * Encoding is strictly additive: a missing binary, a timeout, or a failed
 * conversion all degrade to "no WebP variant" rather than failing the upload -
 * the primary JPEG/PNG asset this produces already satisfies every existing
 * caller, so this is a pure bonus when it works.
 */
class WebpEncoder(
    private val binary: String = "cwebp",
    private val quality: Int = 80,
    private val timeoutSeconds: Long = 10,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Returns WebP-encoded bytes, or null if `cwebp` is unavailable or the conversion fails. */
    fun encode(sourceBytes: ByteArray): ByteArray? {
        val input = File.createTempFile("webp-src-", ".img")
        val output = File.createTempFile("webp-out-", ".webp")
        return try {
            input.writeBytes(sourceBytes)
            val process = ProcessBuilder(
                binary, "-quiet", "-q", quality.toString(), input.absolutePath, "-o", output.absolutePath,
            ).redirectErrorStream(true).start()

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                log.warn("cwebp timed out after {}s; skipping WebP variant", timeoutSeconds)
                return null
            }
            if (process.exitValue() != 0 || !output.exists() || output.length() == 0L) {
                log.warn("cwebp exited {}; skipping WebP variant", process.exitValue())
                return null
            }
            output.readBytes()
        } catch (e: IOException) {
            log.warn("cwebp unavailable ({}); skipping WebP variant", e.message)
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } finally {
            input.delete()
            output.delete()
        }
    }
}
