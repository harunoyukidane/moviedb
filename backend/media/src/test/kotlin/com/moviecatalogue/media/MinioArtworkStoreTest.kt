package com.moviecatalogue.media

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.minio.MinioClient
import io.minio.ObjectWriteResponse
import io.minio.PutObjectArgs
import io.minio.Result
import io.minio.messages.Item
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.MessageDigest

private const val BUCKET = "artwork-test"

class MinioArtworkStoreTest {

    // NoSuchKey handling (exists()->false, open()->null) is covered at the integration
    // level, not here: MockK cannot reliably mock ErrorResponseException (a Throwable
    // subclass) — `every { ... }` fails with "Missing mocked calls" since the JVM's own
    // exception machinery interferes with MockK's call recording.

    @Test
    fun `put generates a UUID-shaped key with the sanitized extension`() {
        val client = mockk<MinioClient>(relaxed = true)
        val store = MinioArtworkStore(client, BUCKET)

        val jpg = store.put(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "jpg")
        assertThat(jpg.storageKey).matches("[0-9a-f-]{36}\\.jpg")

        val unknown = store.put(ByteArrayInputStream(byteArrayOf(1)), "exe")
        assertThat(unknown.storageKey).matches("[0-9a-f-]{36}")
        assertThat(unknown.storageKey).doesNotContain(".exe")
    }

    @Test
    fun `put computes byte size and sha256 by digesting the stream, not trusting the SDK response`() {
        val client = mockk<MinioClient>()
        val data = "hello-minio-bytes".toByteArray()
        every { client.putObject(any()) } answers {
            firstArg<PutObjectArgs>().stream().readBytes()
            mockk<ObjectWriteResponse>(relaxed = true)
        }
        val store = MinioArtworkStore(client, BUCKET)

        val stored = store.put(ByteArrayInputStream(data), "png")

        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(data)
            .joinToString("") { "%02x".format(it) }
        assertThat(stored.byteSize).isEqualTo(data.size.toLong())
        assertThat(stored.sha256).isEqualTo(expectedDigest)
    }

    @Test
    fun `put wraps a generic SDK failure as ArtworkStorageException without leaking details`() {
        val client = mockk<MinioClient>()
        val secret = "connection refused to 10.0.0.5:9000 user=admin"
        every { client.putObject(any()) } throws IOException(secret)
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.put(ByteArrayInputStream(byteArrayOf(1)), "jpg") }
            .isInstanceOf(ArtworkStorageException::class.java)
            .satisfies({ e ->
                assertThat(e.message).doesNotContain(secret)
                assertThat((e as ArtworkStorageException).cause).hasMessage(secret)
            })
    }

    @Test
    fun `listKeys wraps a mid-page listing failure as ArtworkStorageException`() {
        val client = mockk<MinioClient>()
        val item = mockk<Item>()
        every { item.objectName() } returns "kept.png"
        val goodResult = mockk<Result<Item>>()
        every { goodResult.get() } returns item

        // simulates the SDK's lazy iterator failing partway through a page (transient
        // network failure), after already having yielded one item successfully
        val iterable = Iterable<Result<Item>> {
            object : Iterator<Result<Item>> {
                var count = 0
                override fun hasNext() = true
                override fun next(): Result<Item> {
                    count++
                    if (count == 1) return goodResult
                    throw IOException("connection reset mid-page")
                }
            }
        }
        every { client.listObjects(any()) } returns iterable
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.listKeys() }
            .isInstanceOf(ArtworkStorageException::class.java)
    }

    @Test
    fun `listKeysWithAge pairs each key with the SDK-reported last-modified instant`() {
        val client = mockk<MinioClient>()
        val item = mockk<Item>()
        val lastModified = java.time.ZonedDateTime.parse("2026-01-01T00:00:00Z")
        every { item.objectName() } returns "a.png"
        every { item.lastModified() } returns lastModified
        val result = mockk<Result<Item>>()
        every { result.get() } returns item
        every { client.listObjects(any()) } returns listOf(result)
        val store = MinioArtworkStore(client, BUCKET)

        val entries = store.listKeysWithAge()

        assertThat(entries).hasSize(1)
        assertThat(entries.single().key).isEqualTo("a.png")
        assertThat(entries.single().lastModified).isEqualTo(lastModified.toInstant())
    }

    @Test
    fun `listKeysWithAge wraps a mid-page listing failure as ArtworkStorageException`() {
        val client = mockk<MinioClient>()
        every { client.listObjects(any()) } throws IOException("connection reset mid-page")
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.listKeysWithAge() }
            .isInstanceOf(ArtworkStorageException::class.java)
    }

    @Test
    fun `open rejects a traversal-style key before any backend call`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.open("../secret.jpg") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.getObject(any()) }
    }

    @Test
    fun `open rejects a key containing a path separator`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.open("sub/dir/key.jpg") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.getObject(any()) }
    }

    @Test
    fun `exists rejects a key with backslashes`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.exists("..\\..\\etc\\passwd") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.statObject(any()) }
    }

    @Test
    fun `delete rejects a blank key`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.delete("") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.removeObject(any()) }
    }

    @Test
    fun `delete rejects a key with an unexpected extension`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.delete("11111111-1111-1111-1111-111111111111.exe") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.statObject(any()) }
    }

    @Test
    fun `open rejects a key with extra path-like segments appended to a valid uuid`() {
        val client = mockk<MinioClient>()
        val store = MinioArtworkStore(client, BUCKET)

        assertThatThrownBy { store.open("11111111-1111-1111-1111-111111111111.jpg/../../etc/passwd") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(exactly = 0) { client.getObject(any()) }
    }
}
