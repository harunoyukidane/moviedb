package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.comment.CommentRepository
import com.moviecatalogue.catalogue.comment.MovieComment
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.PersonInUseException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.people.PersonData
import com.moviecatalogue.catalogue.reference.CreditRoleCode
import com.moviecatalogue.catalogue.reference.CreditRoleCodeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.util.UUID

class PersonHydratorTest {

    private val peopleClient = mockk<PeopleClient>()
    private val hydrator = PersonHydrator(peopleClient, io.micrometer.core.instrument.simple.SimpleMeterRegistry())

    private fun credit(personId: UUID) =
        MovieCredit(UUID.randomUUID(), UUID.randomUUID(), personId, "ACTOR", CreditCategory.CAST, characterName = "X")

    private fun personData(id: UUID, name: String) =
        PersonData(id, null, name, "", null, null, null, null, 0)

    @Test
    fun `resolves many credits with a single batched gRPC call`() {
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val idsSlot = slot<Collection<UUID>>()
        every { peopleClient.getPeople(capture(idsSlot)) } returns mapOf(
            p1 to personData(p1, "Alice"), p2 to personData(p2, "Bob"),
        )

        // 4 credits referencing only 2 distinct people
        val creditsList = listOf(credit(p1), credit(p2), credit(p1), credit(p2))
        val views = hydrator.toCreditViews(creditsList)

        // exactly ONE gRPC call, with the DISTINCT ids
        verify(exactly = 1) { peopleClient.getPeople(any()) }
        assertThat(idsSlot.captured.toSet()).containsExactlyInAnyOrder(p1, p2)
        assertThat(views).hasSize(4)
        assertThat(views.all { it.person.available }).isTrue()
    }

    @Test
    fun `degraded people service yields available false without failing`() {
        every { peopleClient.getPeople(any()) } throws DependencyUnavailableException()
        val p = UUID.randomUUID()
        val views = hydrator.toCreditViews(listOf(credit(p)))
        assertThat(views).hasSize(1)
        assertThat(views.first().person.available).isFalse()
    }

    @Test
    fun `missing person marked unavailable`() {
        val present = UUID.randomUUID()
        val missing = UUID.randomUUID()
        every { peopleClient.getPeople(any()) } returns mapOf(present to personData(present, "Here"))
        val views = hydrator.toCreditViews(listOf(credit(present), credit(missing)))
        assertThat(views.first { it.person.id == present }.person.available).isTrue()
        assertThat(views.first { it.person.id == missing }.person.available).isFalse()
    }
}

class CreditUseCasesTest {

    private val credits = mockk<CreditRepository>()
    private val movies = mockk<MovieRepository>()
    private val roleCodes = mockk<CreditRoleCodeRepository>()
    private val peopleClient = mockk<PeopleClient>()
    private val hydrator = PersonHydrator(peopleClient, io.micrometer.core.instrument.simple.SimpleMeterRegistry())
    private val useCases = CreditUseCases(credits, movies, roleCodes, peopleClient, hydrator)

    private fun role(code: String, category: CreditCategory, active: Boolean = true) =
        CreditRoleCode(code, code, category, null, "d", active)

    private fun movie(id: UUID, releaseDate: java.time.LocalDate? = null) =
        com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title", releaseDate = releaseDate)

    @Test
    fun `addCredit validates person via gRPC then inserts`() {
        val movieId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        every { movies.findById(movieId) } returns java.util.Optional.of(movie(movieId))
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))
        every { peopleClient.getPerson(personId) } returns PersonData(personId, null, "Star", "", null, null, null, null, 0)
        every { credits.saveAndFlush(any()) } answers { firstArg() }

        val view = useCases.addCredit(AddCreditCommand(movieId, personId, "ACTOR", "Hero", 0))
        assertThat(view.person.available).isTrue()
        assertThat(view.person.name).isEqualTo("Star")
        verify(exactly = 1) { peopleClient.getPerson(personId) }
        verify(exactly = 1) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit rejects a person born after the movie's release, naming both dates`() {
        val movieId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        every { movies.findById(movieId) } returns
            java.util.Optional.of(movie(movieId, releaseDate = java.time.LocalDate.of(2025, 11, 20)))
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))
        every { peopleClient.getPerson(personId) } returns
            PersonData(personId, null, "Jane Doe", "", java.time.LocalDate.of(2026, 4, 4), null, null, null, 0)

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, personId, "ACTOR", "Hero", 0)) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("Jane Doe")
            .hasMessageContaining("2026-04-04")
            .hasMessageContaining("2025-11-20")
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit does not write when People is unavailable`() {
        val movieId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        every { movies.findById(movieId) } returns java.util.Optional.of(movie(movieId))
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))
        every { peopleClient.getPerson(personId) } throws DependencyUnavailableException()

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, personId, "ACTOR", "Hero", 0)) }
            .isInstanceOf(DependencyUnavailableException::class.java)
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit rejects cast without character before any gRPC or write`() {
        val movieId = UUID.randomUUID()
        every { movies.findById(movieId) } returns java.util.Optional.of(movie(movieId))
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, UUID.randomUUID(), "ACTOR", null, 0)) }
            .isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { peopleClient.getPerson(any()) }
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit rejects inactive role`() {
        val movieId = UUID.randomUUID()
        every { movies.findById(movieId) } returns java.util.Optional.of(movie(movieId))
        every { roleCodes.findById("OLD") } returns java.util.Optional.of(role("OLD", CreditCategory.CREW, active = false))

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, UUID.randomUUID(), "OLD", null, 0)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `addCredit rejects unknown movie`() {
        val movieId = UUID.randomUUID()
        every { movies.findById(movieId) } returns java.util.Optional.empty()
        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, UUID.randomUUID(), "ACTOR", "H", 0)) }
            .isInstanceOf(NotFoundException::class.java)
    }
}

class PersonUseCasesTest {

    private val peopleClient = mockk<PeopleClient>(relaxed = true)
    private val credits = mockk<CreditRepository>()
    private val movies = mockk<MovieRepository>()
    private val useCases = PersonUseCases(peopleClient, credits, movies)

    @Test
    fun `deletePerson rejected while referenced`() {
        val id = UUID.randomUUID()
        every { credits.existsByPersonId(id) } returns true
        assertThatThrownBy { useCases.deletePerson(id) }
            .isInstanceOf(PersonInUseException::class.java)
        verify(exactly = 0) { peopleClient.deletePerson(any()) }
    }

    @Test
    fun `deletePerson proceeds when unreferenced`() {
        val id = UUID.randomUUID()
        every { credits.existsByPersonId(id) } returns false
        useCases.deletePerson(id)
        verify(exactly = 1) { peopleClient.deletePerson(id) }
    }
}

class MovieUseCasesTest {

    private val movies = mockk<MovieRepository>()
    private val movieGenres = mockk<com.moviecatalogue.catalogue.movie.MovieGenreRepository>()
    private val genreCodes = mockk<com.moviecatalogue.catalogue.reference.GenreCodeRepository>()
    private val languageCodes = mockk<com.moviecatalogue.catalogue.reference.LanguageCodeRepository>()
    private val credits = mockk<CreditRepository>()
    private val peopleClient = mockk<PeopleClient>()
    private val clock: java.time.Clock = java.time.Clock.fixed(
        java.time.LocalDate.of(2026, 1, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
        java.time.ZoneOffset.UTC,
    )
    private val useCases = MovieUseCases(movies, movieGenres, genreCodes, languageCodes, credits, peopleClient, clock)

    private fun genre(code: String) =
        com.moviecatalogue.catalogue.reference.GenreCode(code, null, code, "d", true, 0)

    private fun language(code: String, active: Boolean = true) =
        com.moviecatalogue.catalogue.reference.LanguageCode(code, code, active, 0)

    @Test
    fun `listMovies with no filter queries with null genre and year`() {
        every { movies.findAllByFilter(null, null, any()) } returns
            org.springframework.data.domain.PageImpl(emptyList())

        val result = useCases.listMovies(20, 0, null)
        assertThat(result.items).isEmpty()
        verify(exactly = 1) { movies.findAllByFilter(null, null, any()) }
    }

    @Test
    fun `listMovies validates genre code exists before querying`() {
        every { genreCodes.findById("GHOST") } returns java.util.Optional.empty()
        assertThatThrownBy {
            useCases.listMovies(20, 0, MovieFilter(genreCode = "GHOST"))
        }.isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { movies.findAllByFilter(any(), any(), any()) }
    }

    @Test
    fun `listMovies allows an existing but inactive genre code`() {
        val inactive = genre("RETIRED").also { it.active = false }
        every { genreCodes.findById("RETIRED") } returns java.util.Optional.of(inactive)
        every { movies.findAllByFilter("RETIRED", null, any()) } returns org.springframework.data.domain.PageImpl(emptyList())

        useCases.listMovies(20, 0, MovieFilter(genreCode = "RETIRED"))
        verify(exactly = 1) { movies.findAllByFilter("RETIRED", null, any()) }
    }

    @Test
    fun `listMovies rejects an out-of-range release year before querying`() {
        assertThatThrownBy {
            useCases.listMovies(20, 0, MovieFilter(releaseYear = 1800))
        }.isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { movies.findAllByFilter(any(), any(), any()) }
    }

    @Test
    fun `listMovies combines genre and year filters`() {
        every { genreCodes.findById("HORROR") } returns java.util.Optional.of(genre("HORROR"))
        every { movies.findAllByFilter("HORROR", 2020, any()) } returns org.springframework.data.domain.PageImpl(emptyList())

        useCases.listMovies(20, 0, MovieFilter(genreCode = "HORROR", releaseYear = 2020))
        verify(exactly = 1) { movies.findAllByFilter("HORROR", 2020, any()) }
    }

    private fun createCommand(originalLanguage: String?) = CreateMovieCommand(
        title = "Title",
        originalTitle = null,
        synopsis = "",
        releaseDate = null,
        runtimeMinutes = null,
        originalLanguage = originalLanguage,
        genreCodes = emptyList(),
    )

    @Test
    fun `createMovie rejects a language code that does not exist`() {
        every { languageCodes.findById("xx") } returns java.util.Optional.empty()
        assertThatThrownBy {
            useCases.createMovie(createCommand(originalLanguage = "xx"))
        }.isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) }
    }

    @Test
    fun `createMovie rejects an inactive language code`() {
        every { languageCodes.findById("la") } returns java.util.Optional.of(language("la", active = false))
        assertThatThrownBy {
            useCases.createMovie(createCommand(originalLanguage = "la"))
        }.isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) }
    }

    @Test
    fun `createMovie allows a null language (unknown original language)`() {
        every { movies.findByTmdbId(any()) } returns null
        every { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) } answers { firstArg() }
        every { movieGenres.flush() } returns Unit

        useCases.createMovie(createCommand(originalLanguage = null))
        verify(exactly = 0) { languageCodes.findById(any()) }
    }

    @Test
    fun `createMovie accepts an active language code`() {
        every { languageCodes.findById("en") } returns java.util.Optional.of(language("en"))
        every { movies.findByTmdbId(any()) } returns null
        every { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) } answers { firstArg() }
        every { movieGenres.flush() } returns Unit

        val result = useCases.createMovie(createCommand(originalLanguage = "en"))
        assertThat(result.originalLanguage).isEqualTo("en")
    }

    @Test
    fun `deleteMovie deletes when present`() {
        val id = UUID.randomUUID()
        val movie = com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title")
        every { movies.findById(id) } returns java.util.Optional.of(movie)
        every { movies.delete(movie) } returns Unit
        every { movies.flush() } returns Unit

        useCases.deleteMovie(id)

        verify(exactly = 1) { movies.delete(movie) }
        verify(exactly = 1) { movies.flush() }
    }

    @Test
    fun `deleteMovie throws NotFound when absent`() {
        val id = UUID.randomUUID()
        every { movies.findById(id) } returns java.util.Optional.empty()

        assertThatThrownBy { useCases.deleteMovie(id) }
            .isInstanceOf(NotFoundException::class.java)
        verify(exactly = 0) { movies.delete(any()) }
    }

    @Test
    fun `deleteMovie throws NotFound, not a raw exception, when lost to a concurrent delete`() {
        // Regression test (V2-16 load test finding). First attempt: a plain
        // existsById-then-deleteById check-then-act let two concurrent deletes of
        // the same movie both pass the check; since Spring Data JPA's deleteById
        // is a silent no-op when the row is already gone (it does NOT throw), the
        // loser "succeeded" despite deleting nothing - worse than the original
        // 500. The fix instead loads the entity and deletes it with an explicit
        // flush, relying on the existing @Version column: Hibernate scopes the
        // DELETE to `WHERE id = ? AND version = ?`, so a row removed by a
        // concurrent delete between our read and our delete raises
        // ObjectOptimisticLockingFailureException here, which we translate to a
        // clean NOT_FOUND.
        val id = UUID.randomUUID()
        val movie = com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title")
        every { movies.findById(id) } returns java.util.Optional.of(movie)
        every { movies.delete(movie) } returns Unit
        every { movies.flush() } throws ObjectOptimisticLockingFailureException("movie", id)

        assertThatThrownBy { useCases.deleteMovie(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    private fun updateCommand(id: UUID, expectedVersion: Long, releaseDate: java.time.LocalDate?) = UpdateMovieCommand(
        id = id,
        expectedVersion = expectedVersion,
        maskTitle = false, title = null,
        maskOriginalTitle = false, originalTitle = null,
        maskSynopsis = false, synopsis = null,
        maskReleaseDate = true, releaseDate = releaseDate,
        maskRuntime = false, runtimeMinutes = null,
        maskOriginalLanguage = false, originalLanguage = null,
        maskGenres = false, genreCodes = null,
    )

    @Test
    fun `updateMovie rejects an out-of-range release date before touching credits or saving`() {
        val id = UUID.randomUUID()
        val movie = com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title")
        every { movies.findById(id) } returns java.util.Optional.of(movie)

        assertThatThrownBy {
            useCases.updateMovie(updateCommand(id, 0, java.time.LocalDate.of(1800, 1, 1)))
        }.isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { credits.findAllByMovieId(any()) }
        verify(exactly = 0) { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) }
    }

    @Test
    fun `updateMovie rejects a release date that precedes a credited person's birth, naming them`() {
        val id = UUID.randomUUID()
        val personId = UUID.randomUUID()
        val movie = com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title")
        every { movies.findById(id) } returns java.util.Optional.of(movie)
        every { credits.findAllByMovieId(id) } returns listOf(
            com.moviecatalogue.catalogue.credit.MovieCredit(
                UUID.randomUUID(), id, personId, "ACTOR", CreditCategory.CAST, characterName = "X",
            ),
        )
        every { peopleClient.getPeople(listOf(personId)) } returns mapOf(
            personId to PersonData(personId, null, "Jane Doe", "", java.time.LocalDate.of(2026, 4, 4), null, null, null, 0),
        )

        assertThatThrownBy {
            useCases.updateMovie(updateCommand(id, 0, java.time.LocalDate.of(2025, 11, 20)))
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("Jane Doe")
        verify(exactly = 0) { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) }
    }

    @Test
    fun `updateMovie allows a release date compatible with all credited people`() {
        val id = UUID.randomUUID()
        val personId = UUID.randomUUID()
        val movie = com.moviecatalogue.catalogue.movie.Movie(id = id, title = "Title")
        every { movies.findById(id) } returns java.util.Optional.of(movie)
        every { credits.findAllByMovieId(id) } returns listOf(
            com.moviecatalogue.catalogue.credit.MovieCredit(
                UUID.randomUUID(), id, personId, "ACTOR", CreditCategory.CAST, characterName = "X",
            ),
        )
        every { peopleClient.getPeople(listOf(personId)) } returns mapOf(
            personId to PersonData(personId, null, "Jane Doe", "", java.time.LocalDate.of(1980, 1, 1), null, null, null, 0),
        )
        every { movies.saveAndFlush(any<com.moviecatalogue.catalogue.movie.Movie>()) } answers { firstArg() }

        val saved = useCases.updateMovie(updateCommand(id, 0, java.time.LocalDate.of(2020, 1, 1)))
        assertThat(saved.releaseDate).isEqualTo(java.time.LocalDate.of(2020, 1, 1))
    }
}

class CommentUseCasesTest {

    private val comments = mockk<CommentRepository>()
    private val movies = mockk<MovieRepository>()
    private val useCases = CommentUseCases(comments, movies)

    @Test
    fun `addComment normalizes fields then inserts, validating the movie exists`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { comments.saveAndFlush(any()) } answers { firstArg() }

        val view = useCases.addComment(movieId, "  Alice  ", "  Great movie!  ")

        assertThat(view.authorDisplayName).isEqualTo("Alice")
        assertThat(view.text).isEqualTo("Great movie!")
        verify(exactly = 1) { comments.saveAndFlush(any()) }
    }

    @Test
    fun `addComment rejects an unknown movie before validating or writing`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns false

        assertThatThrownBy { useCases.addComment(movieId, "Alice", "Great!") }
            .isInstanceOf(NotFoundException::class.java)
        verify(exactly = 0) { comments.saveAndFlush(any()) }
    }

    @Test
    fun `addComment rejects blank author or text`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true

        assertThatThrownBy { useCases.addComment(movieId, "  ", "Great!") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { useCases.addComment(movieId, "Alice", "  ") }
            .isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { comments.saveAndFlush(any()) }
    }

    @Test
    fun `listComments rejects an unknown movie`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns false

        assertThatThrownBy { useCases.listComments(movieId, 20, 0) }
            .isInstanceOf(NotFoundException::class.java)
        verify(exactly = 0) { comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(any(), any()) }
    }

    @Test
    fun `listComments clamps paging and maps the page`() {
        val movieId = UUID.randomUUID()
        val comment = MovieComment(
            id = UUID.randomUUID(), movieId = movieId, authorDisplayName = "Alice", text = "Hi",
            createdAt = java.time.OffsetDateTime.now(),
        )
        every { movies.existsById(movieId) } returns true
        every { comments.findAllByMovieIdOrderByCreatedAtDescIdDesc(movieId, any()) } returns
            org.springframework.data.domain.PageImpl(listOf(comment))

        val page = useCases.listComments(movieId, 9999, -1)
        assertThat(page.limit).isEqualTo(100)
        assertThat(page.offset).isZero()
        assertThat(page.items).hasSize(1)
        assertThat(page.items.first().authorDisplayName).isEqualTo("Alice")
    }
}
