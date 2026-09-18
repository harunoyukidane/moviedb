package com.moviecatalogue.people.person

import com.moviecatalogue.people.ClockConfig
import com.moviecatalogue.people.application.CreatePersonCommand
import com.moviecatalogue.people.application.PeopleApplicationService
import com.moviecatalogue.people.application.SearchPeopleCommand
import com.moviecatalogue.people.application.UpdatePersonCommand
import com.moviecatalogue.people.common.UuidV7
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.VersionConflictException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * Real-Postgres integration for search + CRUD through the application service and
 * repository, exercising the `ix_person_name_lower` case-insensitive path, escaped
 * wildcard literals, Unicode, pagination, and optimistic locking.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PeopleApplicationService::class, ClockConfig::class)
class PeopleSearchIntegrationTest {

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

    @Autowired lateinit var repository: PersonRepository
    @Autowired lateinit var service: PeopleApplicationService
    @Autowired lateinit var dataSource: javax.sql.DataSource

    private fun create(name: String, tmdbId: Long? = null) =
        service.createPerson(CreatePersonCommand(tmdbId, name, "", null, null, null, null))

    @Test
    fun `create get update delete round trip with version check`() {
        val created = create("Christopher Nolan", tmdbId = 525)
        assertThat(created.version).isEqualTo(0)

        val fetched = service.getPerson(created.id)
        assertThat(fetched.name).isEqualTo("Christopher Nolan")

        val updated = service.updatePerson(
            UpdatePersonCommand(
                id = created.id, expectedVersion = 0, maskPaths = setOf("biography"),
                name = null, biography = "British-American director", birthDate = null,
                deathDate = null, placeOfBirth = null, profilePath = null,
            ),
        )
        assertThat(updated.version).isEqualTo(1)
        assertThat(updated.biography).isEqualTo("British-American director")

        // stale version now fails
        assertThatThrownBy {
            service.updatePerson(
                UpdatePersonCommand(
                    id = created.id, expectedVersion = 0, maskPaths = setOf("biography"),
                    name = null, biography = "stale", birthDate = null, deathDate = null,
                    placeOfBirth = null, profilePath = null,
                ),
            )
        }.isInstanceOf(VersionConflictException::class.java)

        service.deletePerson(created.id)
        assertThatThrownBy { service.getPerson(created.id) }
            .isInstanceOf(PersonNotFoundException::class.java)
    }

    @Test
    fun `search is case-insensitive and returns total`() {
        create("Christopher Nolan")
        create("NOLAN North")
        create("Ada Lovelace")

        val result = service.searchPeople(SearchPeopleCommand(query = "nolan", limit = 10, offset = 0))
        assertThat(result.total).isEqualTo(2)
        assertThat(result.people.map { it.name })
            .containsExactlyInAnyOrder("Christopher Nolan", "NOLAN North")
    }

    @Test
    fun `search treats percent and underscore as literals`() {
        create("50% Off")
        create("50xxOff")
        create("a_b marker")
        create("axb marker")

        val percent = service.searchPeople(SearchPeopleCommand("50%", 10, 0))
        assertThat(percent.people.map { it.name }).containsExactly("50% Off")

        val underscore = service.searchPeople(SearchPeopleCommand("a_b", 10, 0))
        assertThat(underscore.people.map { it.name }).containsExactly("a_b marker")
    }

    @Test
    fun `search matches unicode and apostrophes`() {
        create("Björk Guðmundsdóttir")
        create("Conan O'Brien")

        assertThat(service.searchPeople(SearchPeopleCommand("björk", 10, 0)).people).hasSize(1)
        assertThat(service.searchPeople(SearchPeopleCommand("o'brien", 10, 0)).people).hasSize(1)
    }

    @Test
    fun `search pagination returns stable, non-overlapping pages`() {
        (1..5).forEach { create("Actor %02d".format(it)) }

        val page1 = service.searchPeople(SearchPeopleCommand("Actor", limit = 2, offset = 0))
        val page2 = service.searchPeople(SearchPeopleCommand("Actor", limit = 2, offset = 2))
        val page3 = service.searchPeople(SearchPeopleCommand("Actor", limit = 2, offset = 4))

        assertThat(page1.total).isEqualTo(5)
        assertThat(page1.people).hasSize(2)
        assertThat(page2.people).hasSize(2)
        assertThat(page3.people).hasSize(1)

        val all = (page1.people + page2.people + page3.people).map { it.id }
        assertThat(all).doesNotHaveDuplicates()
        assertThat(all).hasSize(5)
    }

    /**
     * V2.5-02: a non-page-aligned offset (7 is not a multiple of the limit,
     * 3) must still land on the correct slice now that the offset is pushed
     * straight to the DB via [com.moviecatalogue.people.common.OffsetPageRequest]
     * instead of being sliced out of an offset+limit fetch-window in memory.
     */
    @Test
    fun `search with a non-page-aligned offset returns the correct slice`() {
        val created = (1..10).map { create("Offset %02d".format(it)) }

        val page = service.searchPeople(SearchPeopleCommand("Offset", limit = 3, offset = 7))

        assertThat(page.total).isEqualTo(10)
        assertThat(page.people.map { it.id }).containsExactly(created[7].id, created[8].id, created[9].id)
    }

    /** V2.5-02: same offset-pushdown fix, for the blank-query "list all" branch. */
    @Test
    fun `blank query list with a non-page-aligned offset returns the correct slice`() {
        val created = (1..10).map { create("Listed %02d".format(it)) }

        val page = service.searchPeople(SearchPeopleCommand(query = "", limit = 3, offset = 7))

        assertThat(page.total).isEqualTo(10)
        assertThat(page.people.map { it.id }).containsExactly(created[7].id, created[8].id, created[9].id)
    }

    @Test
    fun `findAllByIdIn returns matches for a batch`() {
        val a = create("Batch A")
        val b = create("Batch B")
        val found = repository.findAllByIdIn(listOf(a.id, b.id, UuidV7.generate()))
        assertThat(found.map { it.id }).containsExactlyInAnyOrder(a.id, b.id)
    }

    @Test
    fun `existsByTmdbId reflects persisted provenance id`() {
        create("Has Tmdb", tmdbId = 12345)
        assertThat(repository.existsByTmdbId(12345)).isTrue()
        assertThat(repository.existsByTmdbId(99999)).isFalse()
    }

    @Test
    fun `birth and death date persist and read back`() {
        val v = service.createPerson(
            CreatePersonCommand(
                null, "Dated Person", "",
                birthDate = LocalDate.of(1900, 1, 1),
                deathDate = LocalDate.of(1980, 6, 15),
                placeOfBirth = "Somewhere", profilePath = null,
            ),
        )
        val read = service.getPerson(v.id)
        assertThat(read.birthDate).isEqualTo(LocalDate.of(1900, 1, 1))
        assertThat(read.deathDate).isEqualTo(LocalDate.of(1980, 6, 15))
        assertThat(read.placeOfBirth).isEqualTo("Somewhere")
    }

    /**
     * V2.5-01: confirms the GIN trigram index exists with the right operator
     * class for the `%term%` substring search predicate. `enable_seqscan` is
     * disabled so the planner is forced to reveal whether a usable index
     * plan exists at all, rather than picking a seq scan because the test
     * table is tiny — actual seq-scan-vs-index-scan cost tradeoffs at real
     * data volumes are a load-test concern (plans/v2.5), not this test's.
     */
    @Test
    fun `name trigram index serves the substring search predicate`() {
        create("Trigram Probe Person")
        val jdbc = org.springframework.jdbc.core.JdbcTemplate(dataSource)
        jdbc.execute("SET enable_seqscan = off")

        val plan = jdbc.queryForList(
            "EXPLAIN SELECT * FROM person WHERE lower(name) LIKE lower('%robe%') ESCAPE '\\'",
        ).joinToString("\n") { it.values.first().toString() }
        assertThat(plan).contains("ix_person_name_trgm")
    }
}
