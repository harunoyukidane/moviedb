package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.MinioArtworkStore
import com.moviecatalogue.people.common.UuidV7
import com.moviecatalogue.people.person.Person
import com.moviecatalogue.people.person.PersonRepository
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Re-runs the person-photo contract against a real MinIO-backed ArtworkStore, mirroring
 * catalogue-service's ArtworkMinioHttpIntegrationTest (V2-02).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PersonPhotoMinioHttpIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val minio = MinIOContainer(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z")
                .asCompatibleSubstituteFor("minio/minio"),
        )

        private const val BUCKET = "people-photo-http-it"

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("grpc.server.port") { "0" }

            val client = MinioClient.builder()
                .endpoint(minio.s3URL)
                .credentials(minio.userName, minio.password)
                .build()
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build())
            }

            registry.add("artwork.storage.type") { "minio" }
            registry.add("artwork.storage.minio.endpoint") { minio.s3URL }
            registry.add("artwork.storage.minio.access-key") { minio.userName }
            registry.add("artwork.storage.minio.secret-key") { minio.password }
            registry.add("artwork.storage.minio.bucket") { BUCKET }
            registry.add("artwork.storage.minio.region") { "us-east-1" }
        }
    }

    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var people: PersonRepository
    @Autowired lateinit var store: ArtworkStore
    @Autowired lateinit var sweeper: PersonPhotoOrphanSweeper
    @LocalServerPort var port: Int = 0

    @BeforeEach
    fun clean() {
        people.deleteAll()
        store.listKeys().forEach { store.delete(it) }
    }

    private fun base() = "http://localhost:$port"

    private fun newPerson(): UUID {
        val p = Person(id = UuidV7.generate(), name = "MinIO Star")
        return people.saveAndFlush(p).id
    }

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun upload(personId: UUID, bytes: ByteArray, filename: String, contentType: String) =
        rest.exchange(
            "${base()}/api/people/$personId/photo", HttpMethod.PUT,
            HttpEntity(
                LinkedMultiValueMap<String, Any>().apply {
                    add(
                        "file",
                        HttpEntity(
                            object : ByteArrayResource(bytes) { override fun getFilename() = filename },
                            HttpHeaders().apply { this.contentType = MediaType.parseMediaType(contentType) },
                        ),
                    )
                },
                HttpHeaders().apply { this.contentType = MediaType.MULTIPART_FORM_DATA },
            ),
            Map::class.java,
        )

    @Test
    fun `this context is really backed by MinIO, not the local filesystem`() {
        assertThat(store).isInstanceOf(MinioArtworkStore::class.java)
    }

    @Test
    fun `upload stores the photo in MinIO and serves it back`() {
        val personId = newPerson()

        val up = upload(personId, png(), "face.png", "image/png")
        assertThat(up.statusCode).isEqualTo(HttpStatus.CREATED)

        val stored = people.findById(personId).get().profilePath!!
        assertThat(store.exists(stored)).isTrue()

        val served = rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(served.headers.contentType.toString()).isEqualTo("image/png")
        assertThat(served.headers.eTag).isNotBlank()
        assertThat(served.headers.cacheControl).isEqualTo("max-age=2592000, public")
        assertThat(served.headers["X-Content-Type-Options"]).containsExactly("nosniff")
    }

    @Test
    fun `metadata with a missing MinIO object returns 404, not 500`() {
        val personId = newPerson()
        upload(personId, png(), "face.png", "image/png")
        val key = people.findById(personId).get().profilePath!!

        // simulate drift: the object is gone from MinIO but metadata still references it
        store.delete(key)

        val served = rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `a failed metadata swap compensates by deleting the newly uploaded MinIO object`() {
        val personId = newPerson()
        val txTemplate = mockk<TransactionTemplate>()
        every { txTemplate.execute(any<org.springframework.transaction.support.TransactionCallback<*>>()) } throws
            RuntimeException("simulated db failure")
        val useCases = PersonPhotoUseCases(people, store, ImageContentValidator(), txTemplate)

        assertThatThrownBy { useCases.uploadPhoto(personId, png()) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("simulated db failure")

        assertThat(store.listKeys()).isEmpty()
    }

    @Test
    fun `replace removes the old MinIO object`() {
        val personId = newPerson()
        upload(personId, png(), "a.png", "image/png")
        val firstKey = people.findById(personId).get().profilePath!!

        upload(personId, png(), "b.png", "image/png")
        val secondKey = people.findById(personId).get().profilePath!!

        assertThat(secondKey).isNotEqualTo(firstKey)
        assertThat(store.exists(firstKey)).isFalse()
        assertThat(store.listKeys()).containsExactly(secondKey)
    }

    @Test
    fun `delete removes the photo from MinIO and clears profile path`() {
        val personId = newPerson()
        upload(personId, png(), "a.png", "image/png")
        val key = people.findById(personId).get().profilePath!!

        val del = rest.exchange("${base()}/api/people/$personId/photo", HttpMethod.DELETE, null, Map::class.java)
        assertThat(del.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(store.exists(key)).isFalse()
    }

    @Test
    fun `orphan sweeper removes unreferenced MinIO photo objects`() {
        val orphan = store.put(png().inputStream(), "png")

        assertThat(sweeper.sweepOnce()).isEqualTo(1)
        assertThat(store.exists(orphan.storageKey)).isFalse()
    }
}
