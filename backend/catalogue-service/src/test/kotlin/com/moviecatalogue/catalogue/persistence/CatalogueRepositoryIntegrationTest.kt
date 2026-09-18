package com.moviecatalogue.catalogue.persistence

import com.moviecatalogue.catalogue.artwork.ArtworkAsset
import com.moviecatalogue.catalogue.artwork.ArtworkRepository
import com.moviecatalogue.catalogue.comment.CommentRepository
import com.moviecatalogue.catalogue.comment.MovieComment
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
import com.moviecatalogue.catalogue.reference.LanguageCodeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.time.OffsetDateTime
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
    @Autowired lateinit var languages: LanguageCodeRepository
    @Autowired lateinit var artwork: ArtworkRepository
    @Autowired lateinit var comments: CommentRepository
    @Autowired lateinit var dataSource: javax.sql.DataSource

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
        comments.saveAndFlush(
            MovieComment(
                id = UuidV7.generate(), movieId = movie.id, authorDisplayName = "Alice", text = "Great!",
                createdAt = OffsetDateTime.now(),
            ),
        )
        assertThat(movieGenres.findAllByIdMovieId(movie.id)).hasSize(1)
        assertThat(artwork.findByMovieId(movie.id)).isNotNull()
        assertThat(comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(movie.id, PageRequest.of(0, 20)).content)
            .hasSize(1)

        movies.deleteById(movie.id)
        movies.flush()

        assertThat(movieGenres.findAllByIdMovieId(movie.id)).isEmpty()
        assertThat(credits.findAllByMovieId(movie.id)).isEmpty()
        assertThat(artwork.findByMovieId(movie.id)).isNull()
        assertThat(comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(movie.id, PageRequest.of(0, 20)).content)
            .isEmpty()
    }

    @Test
    fun `comment persists and pages in reverse chronological order with id tie-break`() {
        val movie = movies.saveAndFlush(newMovie("Commented"))
        val other = movies.saveAndFlush(newMovie("Other"))
        val now = OffsetDateTime.now()
        comments.saveAndFlush(
            MovieComment(
                id = UuidV7.generate(), movieId = other.id, authorDisplayName = "Zoe", text = "Not this one",
                createdAt = now,
            ),
        )

        val saved = (1..3).map {
            comments.saveAndFlush(
                MovieComment(
                    id = UuidV7.generate(),
                    movieId = movie.id,
                    authorDisplayName = "Author $it",
                    text = "Comment $it",
                    // Same timestamp for every comment forces the id tie-break to do the work.
                    createdAt = now,
                ),
            )
        }

        val page = comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(movie.id, PageRequest.of(0, 2))
        assertThat(page.totalElements).isEqualTo(3)
        assertThat(page.content).hasSize(2)
        // UUIDv7 ids are time-ordered, so id desc is a valid tie-break for equal created_at timestamps.
        assertThat(page.content.map { it.id }).containsExactly(saved[2].id, saved[1].id)
    }

    @Test
    fun `movie title search is case-insensitive and treats wildcards literally`() {
        movies.saveAndFlush(newMovie("The Matrix"))
        movies.saveAndFlush(newMovie("100% Legit"))
        movies.saveAndFlush(newMovie("100 Percent"))

        val page = org.springframework.data.domain.PageRequest.of(0, 20)
        // case-insensitive substring
        assertThat(movies.searchByTitlePattern("%matrix%", page).map { it.title })
            .containsExactly("The Matrix")
        // % is escaped -> literal, so "100\%" only matches "100% Legit"
        assertThat(movies.searchByTitlePattern("%100\\%%", page).map { it.title })
            .containsExactly("100% Legit")
    }

    @Test
    fun `movie filter combines genre and year with AND semantics without duplicates`() {
        val horrorOnly2020 = movies.saveAndFlush(
            Movie(id = UuidV7.generate(), title = "Horror 2020", releaseDate = java.time.LocalDate.of(2020, 5, 1)),
        )
        val horrorAndComedy2020 = movies.saveAndFlush(
            Movie(id = UuidV7.generate(), title = "Horror Comedy 2020", releaseDate = java.time.LocalDate.of(2020, 6, 1)),
        )
        val horror2021 = movies.saveAndFlush(
            Movie(id = UuidV7.generate(), title = "Horror 2021", releaseDate = java.time.LocalDate.of(2021, 1, 1)),
        )
        val comedyOnly2020 = movies.saveAndFlush(
            Movie(id = UuidV7.generate(), title = "Comedy 2020", releaseDate = java.time.LocalDate.of(2020, 7, 1)),
        )
        movieGenres.saveAndFlush(MovieGenre(MovieGenreId(horrorOnly2020.id, "HORROR")))
        movieGenres.saveAndFlush(MovieGenre(MovieGenreId(horrorAndComedy2020.id, "HORROR")))
        movieGenres.saveAndFlush(MovieGenre(MovieGenreId(horrorAndComedy2020.id, "PSYCHOLOGICAL_HORROR")))
        movieGenres.saveAndFlush(MovieGenre(MovieGenreId(horror2021.id, "HORROR")))

        // Unsorted: findAllByFilter is a native query with its own embedded
        // ORDER BY (lower(title) COLLATE "und-x-icu") - an explicit Sort here
        // would make Spring Data try to append another ORDER BY onto the raw
        // SQL text, which breaks the native query with a syntax error.
        val page = PageRequest.of(0, 20)

        // genre-only: a movie assigned to two genres appears exactly once
        val byGenre = movies.findAllByFilter("HORROR", null, page)
        assertThat(byGenre.totalElements).isEqualTo(3)
        assertThat(byGenre.content.map { it.id }).containsExactlyInAnyOrder(
            horrorOnly2020.id, horrorAndComedy2020.id, horror2021.id,
        )

        // year-only
        val byYear = movies.findAllByFilter(null, 2020, page)
        assertThat(byYear.content.map { it.id }).containsExactlyInAnyOrder(
            horrorOnly2020.id, horrorAndComedy2020.id, comedyOnly2020.id,
        )

        // combined AND
        val combined = movies.findAllByFilter("HORROR", 2020, page)
        assertThat(combined.content.map { it.id }).containsExactlyInAnyOrder(
            horrorOnly2020.id, horrorAndComedy2020.id,
        )

        // cleared filter (both null) returns everything
        val cleared = movies.findAllByFilter(null, null, page)
        assertThat(cleared.totalElements).isEqualTo(4)

        // empty result
        val empty = movies.findAllByFilter("HORROR", 1999, page)
        assertThat(empty.totalElements).isZero()
        assertThat(empty.content).isEmpty()

        // paginated (unsorted - see note above)
        val firstPage = movies.findAllByFilter("HORROR", null, PageRequest.of(0, 2))
        assertThat(firstPage.content).hasSize(2)
        assertThat(firstPage.totalElements).isEqualTo(3)
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

        assertThat(languages.findAllByActiveTrueOrderByDisplayOrderAsc().map { it.code })
            .contains("en", "fr", "ja")
        assertThat(languages.findById("en").get().name).isEqualTo("English")
    }

    @Test
    fun `movie original_language is constrained to the controlled language_code table`() {
        val movie = movies.saveAndFlush(newMovie("Language FK").also { it.originalLanguage = "en" })
        assertThat(movies.findById(movie.id).get().originalLanguage).isEqualTo("en")

        movie.originalLanguage = "xx"
        assertThatThrownBy { movies.saveAndFlush(movie) }
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException::class.java)
    }

    /**
     * V2.5-01: confirms the GIN trigram indexes exist with the right operator
     * class for the `%term%` substring search predicate. `enable_seqscan` is
     * disabled so the planner is forced to reveal whether a usable index
     * plan exists at all, rather than picking a seq scan because the test
     * table is tiny — actual seq-scan-vs-index-scan cost tradeoffs at real
     * data volumes are a load-test concern (plans/v2.5), not this test's.
     */
    @Test
    fun `title and original_title trigram indexes serve the substring search predicate`() {
        movies.saveAndFlush(newMovie("Trigram Probe Movie"))
        val jdbc = org.springframework.jdbc.core.JdbcTemplate(dataSource)
        jdbc.execute("SET enable_seqscan = off")

        val titlePlan = jdbc.queryForList(
            "EXPLAIN SELECT * FROM movie WHERE lower(title) LIKE lower('%robe%') ESCAPE '\\'",
        ).joinToString("\n") { it.values.first().toString() }
        assertThat(titlePlan).contains("ix_movie_title_trgm")

        val originalTitlePlan = jdbc.queryForList(
            "EXPLAIN SELECT * FROM movie WHERE lower(original_title) LIKE lower('%robe%') ESCAPE '\\'",
        ).joinToString("\n") { it.values.first().toString() }
        assertThat(originalTitlePlan).contains("ix_movie_original_title_trgm")
    }
}
