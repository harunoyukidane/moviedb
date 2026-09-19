package com.moviecatalogue.media

import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.Semaphore
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
 *
 * (V2.6-04) `encode` runs synchronously on the caller's (upload request)
 * thread - each caller still pays up to [timeoutSeconds] of latency - but
 * concurrent invocations are capped at [maxConcurrent] via [permits], so a
 * burst of uploads can no longer fork an unbounded number of `cwebp`
 * processes and temp files against a small resource pool; excess callers
 * queue for a permit instead.
 */
class WebpEncoder(
    private val binary: String = "cwebp",
    private val quality: Int = 80,
    private val timeoutSeconds: Long = 10,
    maxConcurrent: Int = 4,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val permits = Semaphore(maxConcurrent)

    /** Returns WebP-encoded bytes, or null if `cwebp` is unavailable or the conversion fails. */
    fun encode(sourceBytes: ByteArray): ByteArray? {
        var acquired = false
        try {
            permits.acquire()
            acquired = true
            return encodeWithinLimit(sourceBytes)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            if (acquired) permits.release()
        }
    }

    private fun encodeWithinLimit(sourceBytes: ByteArray): ByteArray? {
        val input = File.createTempFile("webp-src-", ".img")
        val output = File.createTempFile("webp-out-", ".webp")
        return try {
            input.writeBytes(sourceBytes)
            val process = ProcessBuilder(
                binary, "-quiet", "-q", quality.toString(), input.absolutePath, "-o", output.absolutePath,
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

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
        } finally {
            input.delete()
            output.delete()
        }
    }
}
