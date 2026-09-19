package com.moviecatalogue.catalogue.artwork

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.media.ArtworkStore
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.util.LinkedMultiValueMap
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArtworkHttpIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        val artworkDir = Files.createTempDirectory("artwork-it")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("artwork.storage-path") { artworkDir.toString() }
            registry.add("catalogue.artwork.sweep.min-age") { "PT0S" }
            // avoid needing a People gRPC server for this HTTP-only test
            registry.add("grpc.client.people-service.address") { "in-process:artwork-it" }
        }
    }

    @Autowired lateinit var rest: TestRestTemplate
    @Autowired lateinit var movies: MovieRepository
    @Autowired lateinit var store: ArtworkStore
    @Autowired lateinit var sweeper: ArtworkOrphanSweeper
    @Autowired lateinit var artworkRepository: ArtworkRepository
    @LocalServerPort var port: Int = 0

    @org.junit.jupiter.api.BeforeEach
    fun clean() {
        // isolate tests: clear metadata, movies, and any stored files
        artworkRepository.deleteAll()
        movies.deleteAll()
        store.listKeys().forEach { store.delete(it) }
    }

    private fun base() = "http://localhost:$port"

    private fun newMovie(): UUID {
        val m = movies.saveAndFlush(Movie(id = UuidV7.generate(), title = "Art Movie"))
        return m.id
    }

    private fun image(format: String, w: Int = 8, h: Int = 8): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out)
        return out.toByteArray()
    }

    private fun uploadFile(movieId: UUID, bytes: ByteArray, filename: String, contentType: String): org.springframework.http.ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { this.contentType = MediaType.MULTIPART_FORM_DATA }
        val part = object : ByteArrayResource(bytes) {
            override fun getFilename() = filename
        }
        val partHeaders = HttpHeaders().apply { this.contentType = MediaType.parseMediaType(contentType) }
        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", HttpEntity(part, partHeaders))
        return rest.exchange(
            "${base()}/api/movies/$movieId/artwork", HttpMethod.PUT,
            HttpEntity(body, headers), Map::class.java,
        )
    }

    @Test
    fun `upload replace serve and delete a movie poster`() {
        val movieId = newMovie()

        // upload a PNG
        val up = uploadFile(movieId, image("png"), "poster.png", "image/png")
        assertThat(up.statusCode).isEqualTo(HttpStatus.CREATED)
        val artworkId = up.body!!["id"] as String
        val url = up.body!!["url"] as String
        assertThat(url).isEqualTo("/api/artwork/$artworkId")

        // serve it: correct headers + nosniff + ETag
        val served = rest.getForEntity("${base()}$url", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(served.headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(served.headers.contentType.toString()).isEqualTo("image/png")
        val etag = served.headers.eTag
        assertThat(etag).isNotBlank()

        // conditional GET returns 304
        val cond = rest.exchange(
            "${base()}$url", HttpMethod.GET,
            HttpEntity<Void>(HttpHeaders().apply { set(HttpHeaders.IF_NONE_MATCH, etag) }), ByteArray::class.java,
        )
        assertThat(cond.statusCode).isEqualTo(HttpStatus.NOT_MODIFIED)

        // replace with a JPEG; old file should be gone afterwards
        val oldKeyCount = store.listKeys().size
        val up2 = uploadFile(movieId, image("jpg"), "poster.jpg", "image/jpeg")
        assertThat(up2.statusCode).isEqualTo(HttpStatus.CREATED)
        // still exactly one file for this movie (old removed after commit)
        assertThat(store.listKeys().size).isEqualTo(oldKeyCount)

        // delete
        val del = rest.exchange("${base()}/api/movies/$movieId/artwork", HttpMethod.DELETE, null, Map::class.java)
        assertThat(del.statusCode).isEqualTo(HttpStatus.OK)
        // serving now 404s
        val gone = rest.getForEntity("${base()}$url", ByteArray::class.java)
        assertThat(gone.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `unknown artwork id returns 404`() {
        val r = rest.getForEntity("${base()}/api/artwork/${UUID.randomUUID()}", ByteArray::class.java)
        assertThat(r.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `artwork metadata whose object is missing returns 404`() {
        val movieId = newMovie()
        val uploaded = uploadFile(movieId, image("png"), "poster.png", "image/png")
        val artworkId = uploaded.body!!["id"] as String
        val key = artworkRepository.findById(UUID.fromString(artworkId)).get().storageKey
        store.delete(key)

        val response = rest.getForEntity("${base()}/api/artwork/$artworkId", ByteArray::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `spoofed content type jpg that is not an image is rejected`() {
        val movieId = newMovie()
        val notImage = "this is plain text pretending to be a jpg".toByteArray()
        val r = uploadFile(movieId, notImage, "evil.jpg", "image/jpeg")
        assertThat(r.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        assertThat(r.body!!["code"]).isEqualTo("UNSUPPORTED_MEDIA_TYPE")
        // nothing stored
        assertThat(store.listKeys()).isEmpty()
    }

    @Test
    fun `gif is rejected as unsupported`() {
        val movieId = newMovie()
        val gif = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte()) + ByteArray(64)
        val r = uploadFile(movieId, gif, "anim.gif", "image/gif")
        assertThat(r.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    }

    @Test
    fun `filename traversal never influences storage key`() {
        val movieId = newMovie()
        val r = uploadFile(movieId, image("png"), "../../../../etc/passwd.png", "image/png")
        assertThat(r.statusCode).isEqualTo(HttpStatus.CREATED)
        // stored key is a UUID.png with no path segments
        val keys = store.listKeys()
        assertThat(keys).hasSize(1)
        assertThat(keys[0]).matches("[0-9a-f-]{36}\\.png")
        assertThat(keys[0]).doesNotContain("etc").doesNotContain("..")
    }

    @Test
    fun `upload to unknown movie returns 404`() {
        val r = uploadFile(UUID.randomUUID(), image("png"), "p.png", "image/png")
        assertThat(r.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `orphan sweeper removes unreferenced files but keeps referenced ones`() {
        val movieId = newMovie()
        uploadFile(movieId, image("png"), "keep.png", "image/png")
        // simulate an orphan: a stray file with no metadata row
        val orphan = store.put(image("jpg").inputStream(), "jpg")
        assertThat(store.exists(orphan.storageKey)).isTrue()

        val removed = sweeper.sweepOnce()

        assertThat(removed).isGreaterThanOrEqualTo(1)
        assertThat(store.exists(orphan.storageKey)).isFalse()
        // the referenced file is still present
        assertThat(store.listKeys()).hasSize(1)
    }
}
