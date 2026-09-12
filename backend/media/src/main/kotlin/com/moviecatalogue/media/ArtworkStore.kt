package com.moviecatalogue.media

import java.io.InputStream

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
}

/** Result of a store [ArtworkStore.put]: the key plus computed integrity data. */
data class StoredObject(
    val storageKey: String,
    val byteSize: Long,
    val sha256: String,
)
