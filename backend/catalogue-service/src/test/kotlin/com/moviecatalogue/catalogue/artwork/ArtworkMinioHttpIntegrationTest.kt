package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.media.ImageContentValidator
import com.moviecatalogue.media.MinioArtworkStore
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
 * Re-runs the movie-poster contract (upload/serve/replace/delete/orphan-sweep) against a
 * real MinIO-backed ArtworkStore, proving the config-driven wiring (V2-02) actually works
 * end-to-end and not just at the bean-selection level (see ArtworkStoreConfigurationTest).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtworkMinioHttpIntegrationTest {

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

        private const val BUCKET = "catalogue-artwork-http-it"

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("catalogue.artwork.sweep.min-age") { "PT0S" }
            registry.add("grpc.client.people-service.address") { "in-process:artwork-minio-http-it" }

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
    @Autowired lateinit var movies: MovieRepository
    @Autowired lateinit var store: ArtworkStore
    @Autowired lateinit var artworkRepository: ArtworkRepository
    @Autowired lateinit var sweeper: ArtworkOrphanSweeper
    @LocalServerPort var port: Int = 0

    @BeforeEach
    fun clean() {
        artworkRepository.deleteAll()
        movies.deleteAll()
        store.listKeys().forEach { store.delete(it) }
    }

    private fun base() = "http://localhost:$port"

    private fun newMovie(): UUID {
        val m = movies.saveAndFlush(Movie(id = UuidV7.generate(), title = "MinIO Art Movie"))
        return m.id
    }

    private fun image(format: String): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), format, out)
        return out.toByteArray()
    }

    private fun uploadFile(movieId: UUID, bytes: ByteArray, filename: String, contentType: String) =
        rest.exchange(
            "${base()}/api/movies/$movieId/artwork", HttpMethod.PUT,
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
    fun `upload serve and delete a movie poster through MinIO`() {
        val movieId = newMovie()

        val up = uploadFile(movieId, image("png"), "poster.png", "image/png")
        assertThat(up.statusCode).isEqualTo(HttpStatus.CREATED)
        val url = up.body!!["url"] as String

        val served = rest.getForEntity("${base()}$url", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(served.headers.contentType.toString()).isEqualTo("image/png")
        assertThat(served.headers.eTag).isNotBlank()
        assertThat(served.headers.cacheControl).isEqualTo("max-age=31536000, public, immutable")
        assertThat(served.headers.contentLength).isGreaterThan(0)
        assertThat(served.headers["X-Content-Type-Options"]).containsExactly("nosniff")

        val del = rest.exchange("${base()}/api/movies/$movieId/artwork", HttpMethod.DELETE, null, Map::class.java)
        assertThat(del.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(rest.getForEntity("${base()}$url", ByteArray::class.java).statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `metadata with a missing MinIO object returns 404, not 500`() {
        val movieId = newMovie()
        val up = uploadFile(movieId, image("png"), "poster.png", "image/png")
        val url = up.body!!["url"] as String
        val asset = artworkRepository.findByMovieId(movieId)!!

        // simulate drift: the object is gone from MinIO but metadata still references it
        store.delete(asset.storageKey)

        val served = rest.getForEntity("${base()}$url", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `a failed metadata swap compensates by deleting the newly uploaded MinIO object`() {
        val movieId = newMovie()
        val txTemplate = mockk<TransactionTemplate>()
        every { txTemplate.execute(any<org.springframework.transaction.support.TransactionCallback<*>>()) } throws
            RuntimeException("simulated db failure")
        val useCases = ArtworkUseCases(movies, artworkRepository, store, ImageContentValidator(), txTemplate)

        assertThatThrownBy { useCases.uploadMovieArtwork(movieId, image("png"), "poster.png") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("simulated db failure")

        assertThat(store.listKeys()).isEmpty()
    }

    @Test
    fun `replacing a poster removes the previously stored MinIO object`() {
        val movieId = newMovie()
        uploadFile(movieId, image("png"), "a.png", "image/png")
        val oldKeyCount = store.listKeys().size

        val up2 = uploadFile(movieId, image("jpg"), "b.jpg", "image/jpeg")

        assertThat(up2.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(store.listKeys().size).isEqualTo(oldKeyCount)
    }

    @Test
    fun `orphan sweeper removes unreferenced MinIO objects but keeps referenced ones`() {
        newMovie().let { uploadFile(it, image("png"), "keep.png", "image/png") }
        val orphan = store.put(image("jpg").inputStream(), "jpg")

        val removed = sweeper.sweepOnce()

        assertThat(removed).isGreaterThanOrEqualTo(1)
        assertThat(store.exists(orphan.storageKey)).isFalse()
        assertThat(store.listKeys()).hasSize(1)
    }
}
