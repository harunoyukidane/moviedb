package com.moviecatalogue.media

import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveBucketArgs
import io.minio.RemoveObjectArgs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayInputStream
import java.security.MessageDigest

@Testcontainers
class MinioArtworkStoreIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        // Docker Hub removed the minio/minio repository (Oct 2025); quay.io is the
        // current official distribution and mirrors the same tags.
        val minio = MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z")
                .asCompatibleSubstituteFor("minio/minio"),
        )

        private const val BUCKET = "artwork-test"
    }

    private lateinit var client: MinioClient
    private lateinit var store: MinioArtworkStore

    @BeforeEach
    fun setUp() {
        client = MinioClient.builder()
            .endpoint(minio.s3URL)
            .credentials(minio.userName, minio.password)
            .build()
        client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build())
        store = MinioArtworkStore(client, BUCKET)
    }

    @AfterEach
    fun tearDown() {
        val items = client.listObjects(ListObjectsArgs.builder().bucket(BUCKET).recursive(true).build())
        for (item in items) {
            client.removeObject(RemoveObjectArgs.builder().bucket(BUCKET).`object`(item.get().objectName()).build())
        }
        client.removeBucket(RemoveBucketArgs.builder().bucket(BUCKET).build())
    }

    private fun sha256Hex(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test
    fun `put, open, and exists round-trip bytes with the correct key shape, size, and digest`() {
        val data = "hello-real-minio-bytes".toByteArray()
        val stored = store.put(ByteArrayInputStream(data), "jpg")

        assertThat(stored.storageKey).matches("[0-9a-f-]{36}\\.jpg")
        assertThat(stored.byteSize).isEqualTo(data.size.toLong())
        assertThat(stored.sha256).isEqualTo(sha256Hex(data))
        assertThat(store.exists(stored.storageKey)).isTrue()
        store.open(stored.storageKey)!!.use { assertThat(it.readBytes()).isEqualTo(data) }
    }

    @Test
    fun `unknown extension is dropped, persisting a bare-UUID key`() {
        val stored = store.put(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "exe")
        assertThat(stored.storageKey).matches("[0-9a-f-]{36}")
        assertThat(stored.storageKey).doesNotContain(".exe")
    }

    @Test
    fun `open and exists report unknown for a key that was never stored`() {
        assertThat(store.open("11111111-1111-1111-1111-111111111111.jpg")).isNull()
        assertThat(store.exists("11111111-1111-1111-1111-111111111111.jpg")).isFalse()
    }

    @Test
    fun `delete returns true then false on repeat`() {
        val stored = store.put(ByteArrayInputStream(byteArrayOf(9)), "png")
        assertThat(store.delete(stored.storageKey)).isTrue()
        assertThat(store.exists(stored.storageKey)).isFalse()
        assertThat(store.delete(stored.storageKey)).isFalse()
    }

    @Test
    fun `delete of a never-stored key returns false without throwing`() {
        assertThat(store.delete("22222222-2222-2222-2222-222222222222.jpg")).isFalse()
    }

    @Test
    fun `listKeys returns exactly the stored set after a put-delete mix, and empty when the bucket is empty`() {
        assertThat(store.listKeys()).isEmpty()

        val a = store.put(ByteArrayInputStream(byteArrayOf(1)), "jpg")
        val b = store.put(ByteArrayInputStream(byteArrayOf(2)), "png")
        val c = store.put(ByteArrayInputStream(byteArrayOf(3)), "webp")
        store.delete(b.storageKey)

        assertThat(store.listKeys()).containsExactlyInAnyOrder(a.storageKey, c.storageKey)
    }

    @Test
    fun `listKeys fully drains pagination across many objects`() {
        val keys = (1..40).map { store.put(ByteArrayInputStream(byteArrayOf(it.toByte())), "jpg").storageKey }
        assertThat(store.listKeys()).containsExactlyInAnyOrderElementsOf(keys)
    }

    @Test
    fun `open, exists, and delete reject a traversal or slash-containing key even when a real object exists at that raw path`() {
        // Written directly via the raw SDK, bypassing MinioArtworkStore's own key generation -
        // S3 permits '/' in a key, but the adapter's allowlist must still refuse to address it.
        val rawKey = "sub/dir/passwd.jpg"
        client.putObject(
            PutObjectArgs.builder().bucket(BUCKET).`object`(rawKey)
                .stream(ByteArrayInputStream(byteArrayOf(1)), 1, -1)
                .build(),
        )

        assertThatThrownBy { store.open(rawKey) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store.exists(rawKey) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store.delete(rawKey) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { store.open("../secret.jpg") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a multi-MiB payload round-trips correctly`() {
        val data = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        val stored = store.put(ByteArrayInputStream(data), "png")

        assertThat(stored.byteSize).isEqualTo(data.size.toLong())
        assertThat(stored.sha256).isEqualTo(sha256Hex(data))
        store.open(stored.storageKey)!!.use { assertThat(it.readBytes()).isEqualTo(data) }
    }

    @Test
    fun `a real backend failure surfaces as ArtworkStorageException without leaking connection details`() {
        val badClient = MinioClient.builder()
            .endpoint(minio.s3URL)
            .credentials("wrong-access-key", "wrong-secret-key")
            .build()
        val badStore = MinioArtworkStore(badClient, BUCKET)

        assertThatThrownBy { badStore.exists("11111111-1111-1111-1111-111111111111.jpg") }
            .isInstanceOf(ArtworkStorageException::class.java)
            .satisfies({ e ->
                assertThat(e.message).doesNotContain(minio.s3URL)
                assertThat(e.message).doesNotContain("wrong-access-key")
            })
    }
}
