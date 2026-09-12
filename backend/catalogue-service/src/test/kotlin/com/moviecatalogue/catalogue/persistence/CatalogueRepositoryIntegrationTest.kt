package com.moviecatalogue.catalogue.persistence

import com.moviecatalogue.catalogue.artwork.ArtworkAsset
import com.moviecatalogue.catalogue.artwork.ArtworkRepository
import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieGenre
import com.moviecatalogue.catalogue.movie.MovieGenreId
import com.moviecatalogue.catalogue.movie.MovieGenreRepository
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import com.moviecatalogue.catalogue.reference.GenreCodeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Real-Postgres integration for the catalogue aggregates and code tables:
 * paged movie CRUD + optimistic lock, credit persistence + reference queries,
 * genre association, cascade delete, and reference-table reads.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogueRepositoryIntegrationTest {

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

    @Autowired lateinit var movies: MovieRepository
    @Autowired lateinit var credits: CreditRepository
    @Autowired lateinit var movieGenres: MovieGenreRepository
    @Autowired lateinit var genres: GenreCodeRepository
    @Autowired lateinit var roles: CreditRoleCodeRepository
    @Autowired lateinit var artwork: ArtworkRepository

    private fun newMovie(title: String = "Movie") =
        Movie(id = UuidV7.generate(), title = title)

    @Test
    fun `movie crud with paged listing and optimistic lock`() {
        (1..3).forEach { movies.saveAndFlush(newMovie("Movie %02d".format(it))) }

        val page = movies.findAll(PageRequest.of(0, 2, Sort.by("title")))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
        assertThat(page.content.first().title).isEqualTo("Movie 01")

        val m = movies.saveAndFlush(newMovie("Lockable"))
        assertThat(m.version).isEqualTo(0)
        m.title = "Lockable v2"
        assertThat(movies.saveAndFlush(m).version).isEqualTo(1)
    }

    @Test
    fun `credit persists and reference queries work`() {
        val movie = movies.saveAndFlush(newMovie("With Cast"))
        val personId = UuidV7.generate()
        val credit = MovieCredit(
            id = UuidV7.generate(),
            movieId = movie.id,
            personId = personId,
            roleCode = "ACTOR",
            category = CreditCategory.CAST,
            characterName = "Hero",
        )
        credits.saveAndFlush(credit)

        assertThat(credits.findAllByMovieId(movie.id)).hasSize(1)
        assertThat(credits.findAllByPersonId(personId)).hasSize(1)
        assertThat(credits.existsByPersonId(personId)).isTrue()
        assertThat(credits.existsByPersonId(UuidV7.generate())).isFalse()
    }

    @Test
    fun `credits batch across multiple movies`() {
        val m1 = movies.saveAndFlush(newMovie("Batch1"))
        val m2 = movies.saveAndFlush(newMovie("Batch2"))
        credits.saveAndFlush(
            MovieCredit(UuidV7.generate(), m1.id, UuidV7.generate(), "DIRECTOR", CreditCategory.CREW),
        )
        credits.saveAndFlush(
            MovieCredit(UuidV7.generate(), m2.id, UuidV7.generate(), "ACTOR", CreditCategory.CAST, characterName = "X"),
        )
        assertThat(credits.findAllByMovieIdIn(listOf(m1.id, m2.id))).hasSize(2)
    }

    @Test
    fun `genre association and cascade delete removes children`() {
        val movie = movies.saveAndFlush(newMovie("Cascade"))
        movieGenres.saveAndFlush(MovieGenre(MovieGenreId(movie.id, "HORROR")))
        credits.saveAndFlush(
            MovieCredit(UuidV7.generate(), movie.id, UuidV7.generate(), "ACTOR", CreditCategory.CAST, characterName = "Hero"),
        )
        artwork.saveAndFlush(
            ArtworkAsset(
                id = UuidV7.generate(), movieId = movie.id, storageKey = "k-cascade",
                originalFilename = "a.jpg", mediaType = "image/jpeg", byteSize = 1000,
                sha256 = "a".repeat(64),
            ),
        )
        assertThat(movieGenres.findAllByIdMovieId(movie.id)).hasSize(1)
        assertThat(artwork.findByMovieId(movie.id)).isNotNull()

        movies.deleteById(movie.id)
        movies.flush()

        assertThat(movieGenres.findAllByIdMovieId(movie.id)).isEmpty()
        assertThat(credits.findAllByMovieId(movie.id)).isEmpty()
        assertThat(artwork.findByMovieId(movie.id)).isNull()
    }

    @Test
    fun `reference code tables read seeded data with active and category filters`() {
        assertThat(genres.findAllByActiveTrueOrderByDisplayOrderAsc().map { it.code })
            .contains("HORROR", "PSYCHOLOGICAL_HORROR")

        val allRoles = roles.findAllByActiveTrueOrderByDisplayOrderAsc()
        assertThat(allRoles.map { it.code }).contains("ACTOR", "DIRECTOR", "WRITER", "PRODUCER")

        val castRoles = roles.findAllByCategoryAndActiveTrueOrderByDisplayOrderAsc(CreditCategory.CAST)
        assertThat(castRoles.map { it.code }).containsExactly("ACTOR")

        val crewRoles = roles.findAllByCategoryAndActiveTrueOrderByDisplayOrderAsc(CreditCategory.CREW)
        assertThat(crewRoles.map { it.code }).contains("DIRECTOR", "WRITER", "PRODUCER")

        // enum round-trips from the DB credit_category type
        assertThat(roles.findById("ACTOR").get().category).isEqualTo(CreditCategory.CAST)
    }
}
