package com.moviecatalogue.catalogue.application

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

    @Test
    fun `addCredit validates person via gRPC then inserts`() {
        val movieId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
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
    fun `addCredit does not write when People is unavailable`() {
        val movieId = UUID.randomUUID()
        val personId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))
        every { peopleClient.getPerson(personId) } throws DependencyUnavailableException()

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, personId, "ACTOR", "Hero", 0)) }
            .isInstanceOf(DependencyUnavailableException::class.java)
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit rejects cast without character before any gRPC or write`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { roleCodes.findById("ACTOR") } returns java.util.Optional.of(role("ACTOR", CreditCategory.CAST))

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, UUID.randomUUID(), "ACTOR", null, 0)) }
            .isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { peopleClient.getPerson(any()) }
        verify(exactly = 0) { credits.saveAndFlush(any()) }
    }

    @Test
    fun `addCredit rejects inactive role`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns true
        every { roleCodes.findById("OLD") } returns java.util.Optional.of(role("OLD", CreditCategory.CREW, active = false))

        assertThatThrownBy { useCases.addCredit(AddCreditCommand(movieId, UUID.randomUUID(), "OLD", null, 0)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `addCredit rejects unknown movie`() {
        val movieId = UUID.randomUUID()
        every { movies.existsById(movieId) } returns false
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
