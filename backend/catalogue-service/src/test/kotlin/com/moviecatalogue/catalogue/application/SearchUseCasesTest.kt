package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.common.UuidV7
import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.people.PersonHit
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.util.UUID

class SearchUseCasesTest {

    private val movies = mockk<MovieRepository>()
    private val credits = mockk<CreditRepository>()
    private val people = mockk<PeopleClient>()
    private val search = SearchUseCases(movies, credits, people)

    private fun movie(title: String, id: UUID = UuidV7.generate(), original: String? = null) =
        Movie(id = id, title = title, originalTitle = original)

    private fun noCredits() {
        every { credits.findAllByPersonIdIn(any()) } returns emptyList()
    }

    @Test
    fun `blank or whitespace query is rejected`() {
        assertThatThrownBy { search.search("   ", 10, 0) }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { search.search("", 10, 0) }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `overlong query rejected`() {
        assertThatThrownBy { search.search("a".repeat(101), 10, 0) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `escapes wildcard characters into the LIKE pattern`() {
        val patternSlot = slot<String>()
        every { movies.searchByTitlePattern(capture(patternSlot), any<Pageable>()) } returns emptyList()
        every { people.searchPeople(any(), any(), any()) } returns emptyList()
        noCredits()

        search.search("50%", 10, 0)
        // % is escaped so it matches literally, not as a wildcard
        assertThat(patternSlot.captured).isEqualTo("%50\\%%")
    }

    @Test
    fun `ranks exact-prefix title before substring before related-credit`() {
        val prefix = movie("Batman Begins")
        val substring = movie("The Lego Batman Movie")
        val relatedOnly = movie("Some Unrelated Title")
        val personId = UUID.randomUUID()

        // title search returns prefix + substring (order as DB returns)
        every { movies.searchByTitlePattern(any(), any<Pageable>()) } returns listOf(substring, prefix)
        // people search returns a person; their credit is on relatedOnly
        every { people.searchPeople(any(), any(), any()) } returns listOf(PersonHit(personId, "Batman Actor"))
        every { credits.findAllByPersonIdIn(setOf(personId)) } returns listOf(
            MovieCredit(UuidV7.generate(), relatedOnly.id, personId, "ACTOR", CreditCategory.CAST, characterName = "Bruce"),
        )
        every { movies.findAllById(setOf(relatedOnly.id)) } returns listOf(relatedOnly)

        val result = search.search("batman", 10, 0)

        assertThat(result.movies.map { it.title })
            .containsExactly("Batman Begins", "The Lego Batman Movie", "Some Unrelated Title")
        // the related-credit-only movie carries the matched person name
        assertThat(result.movies.last().matchedPersonNames).containsExactly("Batman Actor")
    }

    @Test
    fun `a query matching only a person surfaces that person's movies with matchedPersonNames`() {
        val personId = UUID.randomUUID()
        val creditedMovie = movie("Directed Work")
        every { movies.searchByTitlePattern(any(), any<Pageable>()) } returns emptyList()
        every { people.searchPeople(any(), any(), any()) } returns listOf(PersonHit(personId, "Jane Director"))
        every { credits.findAllByPersonIdIn(setOf(personId)) } returns listOf(
            MovieCredit(UuidV7.generate(), creditedMovie.id, personId, "DIRECTOR", CreditCategory.CREW),
        )
        every { movies.findAllById(setOf(creditedMovie.id)) } returns listOf(creditedMovie)

        val result = search.search("jane", 10, 0)

        assertThat(result.people.map { it.name }).containsExactly("Jane Director")
        assertThat(result.movies).hasSize(1)
        assertThat(result.movies[0].title).isEqualTo("Directed Work")
        assertThat(result.movies[0].matchedPersonNames).containsExactly("Jane Director")
    }

    @Test
    fun `degraded people service still returns movie title results`() {
        every { movies.searchByTitlePattern(any(), any<Pageable>()) } returns listOf(movie("Alien"))
        every { people.searchPeople(any(), any(), any()) } throws DependencyUnavailableException()
        noCredits()

        val result = search.search("alien", 10, 0)
        assertThat(result.movies.map { it.title }).containsExactly("Alien")
        assertThat(result.people).isEmpty()
    }

    @Test
    fun `page limit is clamped to 100`() {
        val pageableSlot = slot<Pageable>()
        every { movies.searchByTitlePattern(any(), capture(pageableSlot)) } returns emptyList()
        every { people.searchPeople(any(), any(), any()) } returns emptyList()
        noCredits()

        search.search("q", 9999, 0)
        // fetch window = clampedLimit(100) + offset(0)
        assertThat(pageableSlot.captured.pageSize).isEqualTo(100)
    }

    @Test
    fun `mixed-case query matches case-insensitively for prefix ranking`() {
        val m = movie("INCEPTION")
        every { movies.searchByTitlePattern(any(), any<Pageable>()) } returns listOf(m)
        every { people.searchPeople(any(), any(), any()) } returns emptyList()
        noCredits()

        val result = search.search("ince", 10, 0)
        assertThat(result.movies).hasSize(1)
        assertThat(result.movies[0].title).isEqualTo("INCEPTION")
    }
}
