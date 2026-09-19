package com.moviecatalogue.media

import java.io.InputStream
import java.time.Instant

/**
 * Storage abstraction for validated artwork bytes (§10). The storage key is
 * always server-generated (UUID + safe extension); the client filename/path is
 * never used to form a key or path. `LocalArtworkStore` is the current impl; an
 * S3-backed impl is a documented future swap (ADR-4).
 */
interface ArtworkStore {
    /** Persist [stream] and return the server-generated storage key. */
    fun put(stream: InputStream, extension: String): StoredObject

    /** Open the stored object for reading, or null if the key is unknown. */
    fun open(storageKey: String): InputStream?

    /** Delete the stored object; returns true if a file was removed. */
    fun delete(storageKey: String): Boolean

    /** True if the key currently resolves to a stored file. */
    fun exists(storageKey: String): Boolean

    /** All storage keys currently present (used by the orphan sweeper). */
    fun listKeys(): List<String>

    /**
     * All storage keys currently present, with each one's last-modified time
     * (V2.6-03). Used by the orphan sweepers to skip objects too young to have
     * finished their upload/encode-and-commit sequence, instead of reaching
     * into a storage-specific SDK for that timestamp.
     */
    fun listKeysWithAge(): List<StoredKey>
}

/** Result of a store [ArtworkStore.put]: the key plus computed integrity data. */
data class StoredObject(
    val storageKey: String,
    val byteSize: Long,
    val sha256: String,
)

/** A storage key paired with when it was last written, from [ArtworkStore.listKeysWithAge]. */
data class StoredKey(
    val key: String,
    val lastModified: Instant,
)
