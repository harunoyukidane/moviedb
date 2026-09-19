package com.moviecatalogue.media

import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import org.slf4j.LoggerFactory
import java.io.FilterInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * S3-compatible [ArtworkStore] backed by a MinIO server (§10, ADR-14). Storage
 * keys are server-generated `UUID.extension`, matching [LocalArtworkStore]'s
 * contract exactly. Assumes the target bucket already exists; bucket
 * bootstrap/policy setup is out of scope here (V2-02/V2-03).
 */
class MinioArtworkStore(
    private val client: MinioClient,
    private val bucket: String,
) : ArtworkStore {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val KEY_PATTERN = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.(jpg|png|webp))?$",
        )
        private const val UPLOAD_PART_SIZE = 10L * 1024 * 1024
        private const val NOT_FOUND_CODE = "NoSuchKey"
    }

    override fun put(stream: InputStream, extension: String): StoredObject {
        val key = "${UUID.randomUUID()}${sanitizeExtension(extension)}"
        val digesting = DigestingInputStream(stream)
        try {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .`object`(key)
                    .stream(digesting, -1, UPLOAD_PART_SIZE)
                    .build(),
            )
        } catch (e: Exception) {
            throw mapError(e)
        }
        return StoredObject(storageKey = key, byteSize = digesting.size(), sha256 = digesting.sha256Hex())
    }

    override fun open(storageKey: String): InputStream? {
        requireValidKey(storageKey)
        return try {
            client.getObject(GetObjectArgs.builder().bucket(bucket).`object`(storageKey).build())
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == NOT_FOUND_CODE) null else throw mapError(e)
        } catch (e: Exception) {
            throw mapError(e)
        }
    }

    override fun exists(storageKey: String): Boolean {
        requireValidKey(storageKey)
        return try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).`object`(storageKey).build())
            true
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == NOT_FOUND_CODE) false else throw mapError(e)
        } catch (e: Exception) {
            throw mapError(e)
        }
    }

    override fun delete(storageKey: String): Boolean {
        requireValidKey(storageKey)
        if (!exists(storageKey)) return false
        return try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(storageKey).build())
            true
        } catch (e: ErrorResponseException) {
            if (e.errorResponse().code() == NOT_FOUND_CODE) {
                log.warn("failed to delete storage key {}: already removed", storageKey)
                false
            } else {
                throw mapError(e)
            }
        } catch (e: Exception) {
            throw mapError(e)
        }
    }

    override fun listKeys(): List<String> {
        val result = mutableListOf<String>()
        try {
            val items = client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())
            for (item in items) {
                result.add(item.get().objectName())
            }
        } catch (e: Exception) {
            throw mapError(e)
        }
        return result
    }

    override fun listKeysWithAge(): List<StoredKey> {
        val result = mutableListOf<StoredKey>()
        try {
            val items = client.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build())
            for (item in items) {
                val stat = item.get()
                result.add(StoredKey(stat.objectName(), stat.lastModified().toInstant()))
            }
        } catch (e: Exception) {
            throw mapError(e)
        }
        return result
    }

    private fun requireValidKey(key: String) {
        if (!KEY_PATTERN.matches(key)) {
            throw IllegalArgumentException("illegal storage key")
        }
    }

    private fun sanitizeExtension(extension: String): String {
        val e = extension.trim().lowercase().removePrefix(".")
        return when (e) {
            "jpg", "jpeg" -> ".jpg"
            "png" -> ".png"
            "webp" -> ".webp"
            else -> ""
        }
    }

    private fun mapError(e: Exception): ArtworkStorageException = ArtworkStorageException(cause = e)

    /** Digests and counts bytes as they flow through, without a second in-memory copy. */
    private class DigestingInputStream(source: InputStream) : FilterInputStream(source) {
        private val digest = MessageDigest.getInstance("SHA-256")
        private var count = 0L
        private var hex: String? = null

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) {
                digest.update(b.toByte())
                count++
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                digest.update(b, off, n)
                count += n
            }
            return n
        }

        fun size(): Long = count

        fun sha256Hex(): String = hex ?: digest.digest().joinToString("") { "%02x".format(it) }.also { hex = it }
    }
}
