package com.moviecatalogue.media

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

class LocalArtworkStoreTest {

    @Test
    fun `put generates a server key, hashes, and stores bytes`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir.resolve("art"))
        val data = "hello-bytes".toByteArray()
        val stored = store.put(ByteArrayInputStream(data), "jpg")

        assertThat(stored.storageKey).endsWith(".jpg")
        // key is a UUID + extension, not derived from any client input
        assertThat(stored.storageKey).matches("[0-9a-f-]{36}\\.jpg")
        assertThat(stored.byteSize).isEqualTo(data.size.toLong())
        assertThat(stored.sha256).hasSize(64)
        assertThat(store.exists(stored.storageKey)).isTrue()

        store.open(stored.storageKey)!!.use {
            assertThat(it.readBytes()).isEqualTo(data)
        }
    }

    @Test
    fun `unknown extension is dropped (no unsafe extension persisted)`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir)
        val stored = store.put(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "exe")
        assertThat(stored.storageKey).matches("[0-9a-f-]{36}")
        assertThat(stored.storageKey).doesNotContain(".exe")
    }

    @Test
    fun `delete removes the file`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir)
        val stored = store.put(ByteArrayInputStream(byteArrayOf(9)), "png")
        assertThat(store.delete(stored.storageKey)).isTrue()
        assertThat(store.exists(stored.storageKey)).isFalse()
        // deleting again is a no-op false, never throws
        assertThat(store.delete(stored.storageKey)).isFalse()
    }

    @Test
    fun `open returns null for unknown key`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir)
        assertThat(store.open("does-not-exist.jpg")).isNull()
    }

    @Test
    fun `path traversal keys are rejected and never escape root`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir.resolve("art"))
        assertThatThrownBy { store.open("../secret.txt") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store.open("..\\..\\etc\\passwd") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store.exists("sub/dir/key.jpg") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `listKeys returns stored files and skips temp files`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir)
        val a = store.put(ByteArrayInputStream(byteArrayOf(1)), "jpg")
        val b = store.put(ByteArrayInputStream(byteArrayOf(2)), "png")
        // a stray temp file must not be reported
        Files.createTempFile(dir, "upload-", ".tmp")
        assertThat(store.listKeys()).containsExactlyInAnyOrder(a.storageKey, b.storageKey)
    }

    @Test
    fun `listKeysWithAge reports each key's filesystem last-modified time and skips temp files`(@TempDir dir: Path) {
        val store = LocalArtworkStore(dir)
        val a = store.put(ByteArrayInputStream(byteArrayOf(1)), "jpg")
        Files.createTempFile(dir, "upload-", ".tmp")

        val entries = store.listKeysWithAge()

        assertThat(entries).hasSize(1)
        assertThat(entries.single().key).isEqualTo(a.storageKey)
        assertThat(entries.single().lastModified)
            .isEqualTo(Files.getLastModifiedTime(dir.resolve(a.storageKey)).toInstant())
    }
}
