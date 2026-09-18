package com.moviecatalogue.people.application

import com.moviecatalogue.people.domain.DuplicateTmdbIdException
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.PreconditionException
import com.moviecatalogue.people.domain.ValidationException
import com.moviecatalogue.people.domain.VersionConflictException
import com.moviecatalogue.people.person.Person
import com.moviecatalogue.people.person.PersonRepository
import com.moviecatalogue.people.reference.CountryCodeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

class PeopleApplicationServiceTest {

    private val repository = mockk<PersonRepository>(relaxed = false)
    private val countryCodes = mockk<CountryCodeRepository>(relaxed = true)
    private val clock: Clock = Clock.fixed(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val service = PeopleApplicationService(repository, countryCodes, clock)

    private fun person(
        id: UUID = UUID.randomUUID(),
        name: String = "Jane Doe",
        version: Long = 0,
        tmdbId: Long? = null,
    ) = Person(id = id, name = name, version = version, tmdbId = tmdbId)

    // --- getPerson ---------------------------------------------------------

    @Test
    fun `getPerson returns view`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.of(person(id = id, name = "Ada"))
        assertThat(service.getPerson(id).name).isEqualTo("Ada")
    }

    @Test
    fun `getPerson throws NotFound when absent`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()
        assertThatThrownBy { service.getPerson(id) }
            .isInstanceOf(PersonNotFoundException::class.java)
    }

    // --- getPeople ---------------------------------------------------------

    @Test
    fun `getPeople dedupes ids and keys by id, missing absent`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val missing = UUID.randomUUID()
        val slot = slot<Collection<UUID>>()
        every { repository.findAllByIdIn(capture(slot)) } returns listOf(
            person(id = b, name = "B"), person(id = a, name = "A"), // out of order on purpose
        )

        val result = service.getPeople(listOf(a, b, a, missing, b))

        // repository queried with de-duplicated ids
        assertThat(slot.captured.toSet()).containsExactlyInAnyOrder(a, b, missing)
        // keyed by id; missing simply absent; order-independent
        assertThat(result.keys).containsExactlyInAnyOrder(a, b)
        assertThat(result[a]!!.name).isEqualTo("A")
        assertThat(result[b]!!.name).isEqualTo("B")
        assertThat(result).doesNotContainKey(missing)
    }

    @Test
    fun `getPeople rejects oversize batch`() {
        val ids = (1..201).map { UUID.randomUUID() }
        assertThatThrownBy { service.getPeople(ids) }
            .isInstanceOf(ValidationException::class.java)
        verify(exactly = 0) { repository.findAllByIdIn(any()) }
    }

    @Test
    fun `getPeople returns empty for empty input without querying`() {
        assertThat(service.getPeople(emptyList())).isEmpty()
        verify(exactly = 0) { repository.findAllByIdIn(any()) }
    }

    // --- searchPeople ------------------------------------------------------

    @Test
    fun `searchPeople clamps limit and returns total`() {
        every { repository.countByNamePattern(any()) } returns 3
        every { repository.searchByNamePattern(any(), any<Pageable>()) } returns listOf(
            person(name = "Nolan"),
        )
        val result = service.searchPeople(SearchPeopleCommand(query = "nol", limit = 9999, offset = 0))
        assertThat(result.total).isEqualTo(3)
        assertThat(result.people).hasSize(1)
    }

    @Test
    fun `searchPeople with a blank query lists all people (paged)`() {
        every { repository.count() } returns 2
        every { repository.findAll(any<Pageable>()) } returns
            org.springframework.data.domain.PageImpl(listOf(person(name = "Ada"), person(name = "Bob")))
        val result = service.searchPeople(SearchPeopleCommand(query = "   ", limit = 10, offset = 0))
        assertThat(result.total).isEqualTo(2)
        assertThat(result.people.map { it.name }).containsExactly("Ada", "Bob")
    }

    @Test
    fun `searchPeople rejects an overlong query`() {
        assertThatThrownBy { service.searchPeople(SearchPeopleCommand("a".repeat(101), 10, 0)) }
            .isInstanceOf(ValidationException::class.java)
    }

    // --- createPerson ------------------------------------------------------

    @Test
    fun `createPerson persists a valid person including death_date`() {
        val saved = slot<Person>()
        every { repository.existsByTmdbId(any()) } returns false
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }

        val view = service.createPerson(
            CreatePersonCommand(
                tmdbId = 42,
                name = "  Bela Lugosi  ",
                biography = "  actor  ",
                birthDate = LocalDate.of(1882, 10, 20),
                deathDate = LocalDate.of(1956, 8, 16),
                placeOfBirth = "Lugos",
                profilePath = null,
            ),
        )
        assertThat(view.name).isEqualTo("Bela Lugosi")
        assertThat(view.deathDate).isEqualTo(LocalDate.of(1956, 8, 16))
        assertThat(saved.captured.biography).isEqualTo("actor")
    }

    @Test
    fun `createPerson rejects duplicate tmdb_id`() {
        every { repository.existsByTmdbId(7) } returns true
        assertThatThrownBy {
            service.createPerson(
                CreatePersonCommand(7, "Dup", "", null, null, null, null),
            )
        }.isInstanceOf(DuplicateTmdbIdException::class.java)
        verify(exactly = 0) { repository.saveAndFlush(any()) }
    }

    @Test
    fun `createPerson rejects blank name and death before birth`() {
        assertThatThrownBy {
            service.createPerson(CreatePersonCommand(null, "  ", "", null, null, null, null))
        }.isInstanceOf(ValidationException::class.java)

        assertThatThrownBy {
            service.createPerson(
                CreatePersonCommand(
                    null, "X", "",
                    birthDate = LocalDate.of(2000, 1, 1),
                    deathDate = LocalDate.of(1990, 1, 1),
                    placeOfBirth = null, profilePath = null,
                ),
            )
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `createPerson accepts an active birth country code`() {
        val saved = slot<Person>()
        every { repository.existsByTmdbId(any()) } returns false
        every { repository.saveAndFlush(capture(saved)) } answers { saved.captured }
        every { countryCodes.findById("US") } returns
            Optional.of(com.moviecatalogue.people.reference.CountryCode(code = "US", name = "United States", active = true))

        service.createPerson(
            CreatePersonCommand(
                tmdbId = null, name = "Jane", biography = "", birthDate = null, deathDate = null,
                placeOfBirth = "Hollywood", profilePath = null, birthCountryCode = "US",
            ),
        )
        assertThat(saved.captured.birthCountryCode).isEqualTo("US")
    }

    @Test
    fun `createPerson rejects an unknown or inactive birth country code`() {
        every { repository.existsByTmdbId(any()) } returns false
        every { countryCodes.findById("ZZ") } returns Optional.empty()
        assertThatThrownBy {
            service.createPerson(
                CreatePersonCommand(
                    tmdbId = null, name = "Jane", biography = "", birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null, birthCountryCode = "ZZ",
                ),
            )
        }.isInstanceOf(ValidationException::class.java)

        every { countryCodes.findById("XX") } returns
            Optional.of(com.moviecatalogue.people.reference.CountryCode(code = "XX", name = "Retired", active = false))
        assertThatThrownBy {
            service.createPerson(
                CreatePersonCommand(
                    tmdbId = null, name = "Jane", biography = "", birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null, birthCountryCode = "XX",
                ),
            )
        }.isInstanceOf(ValidationException::class.java)
    }

    // --- updatePerson ------------------------------------------------------

    @Test
    fun `updatePerson applies only masked fields`() {
        val id = UUID.randomUUID()
        val existing = person(id = id, name = "Old", version = 5)
        existing.biography = "old bio"
        every { repository.findById(id) } returns Optional.of(existing)
        every { repository.saveAndFlush(any()) } answers { firstArg() }

        val view = service.updatePerson(
            UpdatePersonCommand(
                id = id, expectedVersion = 5, maskPaths = setOf("name"),
                name = "New", biography = "IGNORED", birthDate = null, deathDate = null,
                placeOfBirth = null, profilePath = null,
            ),
        )
        assertThat(view.name).isEqualTo("New")
        assertThat(existing.biography).isEqualTo("old bio") // not in mask -> unchanged
    }

    @Test
    fun `updatePerson with only birth_country_code in the mask leaves the rest of the profile untouched`() {
        // Regression: selecting a country and saving must not wipe name, biography,
        // dates or place of birth - only the masked field may change.
        val id = UUID.randomUUID()
        val existing = person(id = id, name = "Al Pacino", version = 5)
        existing.biography = "Legendary American actor known for The Godfather."
        existing.birthDate = LocalDate.of(1940, 4, 25)
        existing.placeOfBirth = "New York City"
        every { repository.findById(id) } returns Optional.of(existing)
        every { repository.saveAndFlush(any()) } answers { firstArg() }
        every { countryCodes.findById("FR") } returns
            Optional.of(com.moviecatalogue.people.reference.CountryCode(code = "FR", name = "France", active = true))

        val view = service.updatePerson(
            UpdatePersonCommand(
                id = id, expectedVersion = 5, maskPaths = setOf("birth_country_code"),
                name = null, biography = null, birthDate = null, deathDate = null,
                placeOfBirth = null, profilePath = null, birthCountryCode = "FR",
            ),
        )

        assertThat(view.birthCountryCode).isEqualTo("FR")
        assertThat(view.name).isEqualTo("Al Pacino")
        assertThat(view.biography).isEqualTo("Legendary American actor known for The Godfather.")
        assertThat(view.birthDate).isEqualTo(LocalDate.of(1940, 4, 25))
        assertThat(view.placeOfBirth).isEqualTo("New York City")
    }

    @Test
    fun `updatePerson maps stale version to VersionConflict`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.of(person(id = id, version = 9))
        assertThatThrownBy {
            service.updatePerson(
                UpdatePersonCommand(
                    id = id, expectedVersion = 3, maskPaths = setOf("name"),
                    name = "X", biography = null, birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null,
                ),
            )
        }.isInstanceOf(VersionConflictException::class.java)
            .hasMessage("person was modified concurrently") // F23: no version numbers in the user-facing message
    }

    @Test
    fun `updatePerson rejects unknown mask path with Precondition`() {
        assertThatThrownBy {
            service.updatePerson(
                UpdatePersonCommand(
                    id = UUID.randomUUID(), expectedVersion = 0, maskPaths = setOf("bogus"),
                    name = null, biography = null, birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null,
                ),
            )
        }.isInstanceOf(PreconditionException::class.java)
    }

    @Test
    fun `updatePerson rejects empty mask with Validation`() {
        assertThatThrownBy {
            service.updatePerson(
                UpdatePersonCommand(
                    id = UUID.randomUUID(), expectedVersion = 0, maskPaths = emptySet(),
                    name = null, biography = null, birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null,
                ),
            )
        }.isInstanceOf(ValidationException::class.java)
    }

    // --- deletePerson ------------------------------------------------------

    @Test
    fun `deletePerson deletes when present`() {
        val id = UUID.randomUUID()
        val p = person(id = id)
        every { repository.findById(id) } returns Optional.of(p)
        every { repository.delete(p) } returns Unit
        every { repository.flush() } returns Unit

        service.deletePerson(id)

        verify(exactly = 1) { repository.delete(p) }
        verify(exactly = 1) { repository.flush() }
    }

    @Test
    fun `deletePerson throws NotFound when absent`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()

        assertThatThrownBy { service.deletePerson(id) }
            .isInstanceOf(PersonNotFoundException::class.java)
        verify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `deletePerson throws NotFound, not a raw exception, when lost to a concurrent delete`() {
        // Regression test (V2-16 load test finding). First attempt: a plain
        // existsById-then-deleteById check-then-act let two concurrent deletes of
        // the same person both pass the check; since Spring Data JPA's deleteById
        // is a silent no-op when the row is already gone (it does NOT throw), the
        // loser "succeeded" despite deleting nothing - worse than the original
        // 500. The fix instead loads the entity and deletes it with an explicit
        // flush, relying on the existing @Version column: Hibernate scopes the
        // DELETE to `WHERE id = ? AND version = ?`, so a row removed by a
        // concurrent delete between our read and our delete raises
        // ObjectOptimisticLockingFailureException here, which we translate to a
        // clean NOT_FOUND.
        val id = UUID.randomUUID()
        val p = person(id = id)
        every { repository.findById(id) } returns Optional.of(p)
        every { repository.delete(p) } returns Unit
        every { repository.flush() } throws org.springframework.orm.ObjectOptimisticLockingFailureException("person", id)

        assertThatThrownBy { service.deletePerson(id) }
            .isInstanceOf(PersonNotFoundException::class.java)
    }
}
