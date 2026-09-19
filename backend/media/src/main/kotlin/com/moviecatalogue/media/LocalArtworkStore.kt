package com.moviecatalogue.media

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Filesystem-backed [ArtworkStore] writing to a mounted volume (§10). Storage
 * keys are server-generated `UUID.extension`; the client filename never touches
 * the key or path. Writes go to a temp file first, hash while streaming, then
 * atomically move into place so readers never see a partial file.
 */
class LocalArtworkStore(
    private val root: Path,
) : ArtworkStore {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        Files.createDirectories(root)
    }

    override fun put(stream: InputStream, extension: String): StoredObject {
        val safeExt = sanitizeExtension(extension)
        val key = "${UUID.randomUUID()}$safeExt"
        val target = resolveKey(key)
        val tmp = Files.createTempFile(root, "upload-", ".tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            Files.newOutputStream(tmp).use { out ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                    size += n
                }
                out.flush()
            }
            // Atomic move so a reader never observes a partially written file.
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(tmp) }
            throw e
        }
        return StoredObject(storageKey = key, byteSize = size, sha256 = digest.digest().toHex())
    }

    override fun open(storageKey: String): InputStream? {
        val path = resolveKey(storageKey)
        return if (Files.isRegularFile(path)) Files.newInputStream(path) else null
    }

    override fun delete(storageKey: String): Boolean =
        runCatching { Files.deleteIfExists(resolveKey(storageKey)) }
            .getOrElse {
                log.warn("failed to delete storage key {}: {}", storageKey, it.message)
                false
            }

    override fun exists(storageKey: String): Boolean = Files.isRegularFile(resolveKey(storageKey))

    override fun listKeys(): List<String> =
        Files.list(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { !it.endsWith(".tmp") }
                .toList()
        }

    override fun listKeysWithAge(): List<StoredKey> =
        try {
            Files.list(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { !it.fileName.toString().endsWith(".tmp") }
                    .map { StoredKey(it.fileName.toString(), Files.getLastModifiedTime(it).toInstant()) }
                    .toList()
            }
        } catch (e: java.io.UncheckedIOException) {
            throw ArtworkStorageException(cause = e)
        } catch (e: java.io.IOException) {
            throw ArtworkStorageException(cause = e)
        }

    /**
     * Resolve a storage key to a path *inside* [root], rejecting any key that
     * escapes the root (path traversal / absolute paths). Keys are server-made,
     * but this is defense in depth against a corrupted/forged key.
     */
    private fun resolveKey(key: String): Path {
        if (key.isBlank() || key.contains('/') || key.contains('\\') || key.contains("..")) {
            throw IllegalArgumentException("illegal storage key")
        }
        val resolved = root.resolve(key).normalize()
        if (!resolved.startsWith(root.normalize())) {
            throw IllegalArgumentException("storage key escapes root")
        }
        return resolved
    }

    private fun sanitizeExtension(extension: String): String {
        val e = extension.trim().lowercase().removePrefix(".")
        // only known-safe image extensions; anything else is dropped
        return when (e) {
            "jpg", "jpeg" -> ".jpg"
            "png" -> ".png"
            "webp" -> ".webp"
            else -> ""
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
