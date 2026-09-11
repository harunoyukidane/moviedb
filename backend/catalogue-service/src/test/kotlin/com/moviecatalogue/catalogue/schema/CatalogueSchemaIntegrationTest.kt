package com.moviecatalogue.catalogue.schema

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Verifies the real Catalogue Flyway migration + seed against real PostgreSQL:
 * seed rows, cast/crew check, composite FK, one primary artwork per movie,
 * movie_genre dedup, cascade delete, byte_size bounds, optimistic lock.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogueSchemaIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        }
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var movies: MovieRepository

    private fun insertMovie(title: String = "Test Movie"): UUID {
        val id = UuidV7.generate()
        jdbc.update(
            "INSERT INTO movie (id, title, synopsis) VALUES (?, ?, '')",
            id, title,
        )
        return id
    }

    @Test
    fun `seed reference data is present`() {
        val roles = jdbc.queryForObject("SELECT count(*) FROM credit_role_code", Int::class.java)
        val genres = jdbc.queryForObject("SELECT count(*) FROM genre_code", Int::class.java)
        assertThat(roles).isGreaterThanOrEqualTo(4)
        assertThat(genres).isGreaterThanOrEqualTo(2)
        assertThat(
            jdbc.queryForObject("SELECT category FROM credit_role_code WHERE code = 'ACTOR'", String::class.java)
        ).isEqualTo("CAST")
    }

    @Test
    fun `cast credit requires character name and crew forbids it`() {
        val movieId = insertMovie()
        // CAST without character_name -> violates check
        assertThatThrownBy {
            jdbc.update(
                "INSERT INTO movie_credit (id, movie_id, person_id, role_code, category) " +
                    "VALUES (?, ?, ?, 'ACTOR', 'CAST')",
                UuidV7.generate(), movieId, UuidV7.generate(),
            )
        }.isInstanceOf(Exception::class.java)

        // CREW with character_name -> violates check
        assertThatThrownBy {
            jdbc.update(
                "INSERT INTO movie_credit (id, movie_id, person_id, role_code, category, character_name) " +
                    "VALUES (?, ?, ?, 'DIRECTOR', 'CREW', 'Nope')",
                UuidV7.generate(), movieId, UuidV7.generate(),
            )
        }.isInstanceOf(Exception::class.java)

        // valid CAST with character_name
        jdbc.update(
            "INSERT INTO movie_credit (id, movie_id, person_id, role_code, category, character_name) " +
                "VALUES (?, ?, ?, 'ACTOR', 'CAST', 'Hero')",
            UuidV7.generate(), movieId, UuidV7.generate(),
        )
    }

    @Test
    fun `composite fk rejects role_code and category mismatch`() {
        val movieId = insertMovie()
        // ACTOR is CAST; declaring it CREW must fail the composite FK
        assertThatThrownBy {
            jdbc.update(
                "INSERT INTO movie_credit (id, movie_id, person_id, role_code, category) " +
                    "VALUES (?, ?, ?, 'ACTOR', 'CREW')",
                UuidV7.generate(), movieId, UuidV7.generate(),
            )
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `only one primary artwork per movie`() {
        val movieId = insertMovie()
        jdbc.update(
            "INSERT INTO artwork_asset (id, movie_id, storage_key, original_filename, media_type, byte_size, sha256) " +
                "VALUES (?, ?, ?, 'a.jpg', 'image/jpeg', 1000, repeat('a',64))",
            UuidV7.generate(), movieId, "key-1",
        )
        assertThatThrownBy {
            jdbc.update(
                "INSERT INTO artwork_asset (id, movie_id, storage_key, original_filename, media_type, byte_size, sha256) " +
                    "VALUES (?, ?, ?, 'b.jpg', 'image/jpeg', 1000, repeat('b',64))",
                UuidV7.generate(), movieId, "key-2",
            )
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `byte_size bounds enforced`() {
        val movieId = insertMovie()
        assertThatThrownBy {
            jdbc.update(
                "INSERT INTO artwork_asset (id, movie_id, storage_key, original_filename, media_type, byte_size, sha256) " +
                    "VALUES (?, ?, 'big', 'big.jpg', 'image/jpeg', 5242881, repeat('a',64))",
                UuidV7.generate(), movieId,
            )
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `movie_genre primary key prevents duplicate assignment`() {
        val movieId = insertMovie()
        jdbc.update("INSERT INTO movie_genre (movie_id, genre_code) VALUES (?, 'HORROR')", movieId)
        assertThatThrownBy {
            jdbc.update("INSERT INTO movie_genre (movie_id, genre_code) VALUES (?, 'HORROR')", movieId)
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `deleting a movie cascades credits genres and artwork`() {
        val movieId = insertMovie()
        jdbc.update("INSERT INTO movie_genre (movie_id, genre_code) VALUES (?, 'HORROR')", movieId)
        jdbc.update(
            "INSERT INTO movie_credit (id, movie_id, person_id, role_code, category, character_name) " +
                "VALUES (?, ?, ?, 'ACTOR', 'CAST', 'Hero')",
            UuidV7.generate(), movieId, UuidV7.generate(),
        )
        jdbc.update(
            "INSERT INTO artwork_asset (id, movie_id, storage_key, original_filename, media_type, byte_size, sha256) " +
                "VALUES (?, ?, 'k', 'a.jpg', 'image/jpeg', 1000, repeat('a',64))",
            UuidV7.generate(), movieId,
        )

        jdbc.update("DELETE FROM movie WHERE id = ?", movieId)

        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_genre WHERE movie_id = ?", Int::class.java, movieId)).isZero
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movie_credit WHERE movie_id = ?", Int::class.java, movieId)).isZero
        assertThat(jdbc.queryForObject("SELECT count(*) FROM artwork_asset WHERE movie_id = ?", Int::class.java, movieId)).isZero
    }

    @Test
    fun `movie version increments via jpa optimistic locking`() {
        val saved = movies.saveAndFlush(Movie(id = UuidV7.generate(), title = "Locked"))
        assertThat(saved.version).isEqualTo(0)
        saved.title = "Locked v2"
        assertThat(movies.saveAndFlush(saved).version).isEqualTo(1)
    }
}
