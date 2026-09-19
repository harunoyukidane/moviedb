package com.moviecatalogue.people.photo

import com.moviecatalogue.media.ArtworkStore
import com.moviecatalogue.people.common.UuidV7
import com.moviecatalogue.people.person.Person
import com.moviecatalogue.people.person.PersonRepository
import org.assertj.core.api.Assertions.assertThat
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
class PersonPhotoHttpIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        val photoDir = Files.createTempDirectory("person-photo-it")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("artwork.storage-path") { photoDir.toString() }
            registry.add("grpc.server.port") { "0" } // random gRPC port to avoid clashes
            registry.add("people.photo.sweep.min-age") { "PT0S" }
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
        val p = Person(id = UuidV7.generate(), name = "Star")
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
                    add("file", HttpEntity(object : ByteArrayResource(bytes) { override fun getFilename() = filename },
                        HttpHeaders().apply { this.contentType = MediaType.parseMediaType(contentType) }))
                },
                HttpHeaders().apply { this.contentType = MediaType.MULTIPART_FORM_DATA },
            ),
            Map::class.java,
        )

    @Test
    fun `upload stores the photo key and serves it locally`() {
        val personId = newPerson()

        val up = upload(personId, png(), "face.png", "image/png")
        assertThat(up.statusCode).isEqualTo(HttpStatus.CREATED)

        // profile_path holds the uploaded storage key (UUID.png)
        val stored = people.findById(personId).get().profilePath!!
        assertThat(stored).matches("[0-9a-f-]{36}\\.png")

        // served locally with nosniff
        val served = rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java)
        assertThat(served.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(served.headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(served.headers.contentType.toString()).isEqualTo("image/png")
    }

    @Test
    fun `validation is identical to movie path - spoofed content rejected`() {
        val personId = newPerson()
        val r = upload(personId, "not an image".toByteArray(), "evil.png", "image/png")
        assertThat(r.statusCode).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        assertThat(store.listKeys()).isEmpty()
    }

    @Test
    fun `replace removes the old uploaded file`() {
        val personId = newPerson()
        upload(personId, png(), "a.png", "image/png")
        val firstKey = people.findById(personId).get().profilePath!!
        assertThat(store.exists(firstKey)).isTrue()

        upload(personId, png(), "b.png", "image/png")
        val secondKey = people.findById(personId).get().profilePath!!
        assertThat(secondKey).isNotEqualTo(firstKey)
        // old file removed, only the new one remains
        assertThat(store.exists(firstKey)).isFalse()
        assertThat(store.listKeys()).containsExactly(secondKey)
    }

    @Test
    fun `delete removes photo and clears profile path`() {
        val personId = newPerson()
        upload(personId, png(), "a.png", "image/png")
        val key = people.findById(personId).get().profilePath!!

        val del = rest.exchange("${base()}/api/people/$personId/photo", HttpMethod.DELETE, null, Map::class.java)
        assertThat(del.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(people.findById(personId).get().profilePath).isNull()
        assertThat(store.exists(key)).isFalse()
        // serving now 404s (no uploaded photo)
        assertThat(rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java).statusCode)
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `upload to unknown person returns 404`() {
        val r = upload(UUID.randomUUID(), png(), "a.png", "image/png")
        assertThat(r.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `serving a person with no uploaded photo returns 404`() {
        val personId = newPerson()
        assertThat(rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java).statusCode)
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `serving metadata whose object is missing returns 404`() {
        val personId = newPerson()
        upload(personId, png(), "face.png", "image/png")
        val key = people.findById(personId).get().profilePath!!
        store.delete(key)

        assertThat(rest.getForEntity("${base()}/api/people/$personId/photo", ByteArray::class.java).statusCode)
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `orphan sweeper removes unreferenced photo objects`() {
        val orphan = store.put(png().inputStream(), "png")

        assertThat(sweeper.sweepOnce()).isEqualTo(1)
        assertThat(store.exists(orphan.storageKey)).isFalse()
    }
}
