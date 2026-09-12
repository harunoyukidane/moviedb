package com.moviecatalogue.people.person

import com.moviecatalogue.people.common.UuidV7
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * Verifies the real Flyway migration against real PostgreSQL:
 * - app-supplied UUIDv7 inserts
 * - death_date < birth_date rejected; >= / null accepted
 * - @Version optimistic-lock increments and conflicts
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersonRepositoryIntegrationTest {

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

    @Autowired
    lateinit var repository: PersonRepository

    @Autowired
    lateinit var entityManager: TestEntityManager

    private fun newPerson(name: String = "Jane Doe") =
        Person(id = UuidV7.generate(), name = name)

    @Test
    fun `inserts a person with app-supplied uuidv7`() {
        val saved = repository.saveAndFlush(newPerson())
        assertThat(saved.id.version()).isEqualTo(7)
        assertThat(repository.findById(saved.id)).isPresent
    }

    @Test
    fun `accepts death_date equal to or after birth_date and null`() {
        val p = newPerson().apply {
            birthDate = LocalDate.of(1950, 1, 1)
            deathDate = LocalDate.of(2000, 1, 1)
        }
        assertThat(repository.saveAndFlush(p).id).isNotNull
        assertThat(repository.saveAndFlush(newPerson("No dates")).id).isNotNull
    }

    @Test
    fun `rejects death_date before birth_date`() {
        val p = newPerson().apply {
            birthDate = LocalDate.of(2000, 1, 1)
            deathDate = LocalDate.of(1950, 1, 1)
        }
        assertThatThrownBy { repository.saveAndFlush(p) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `version increments on update`() {
        val saved = repository.saveAndFlush(newPerson())
        assertThat(saved.version).isEqualTo(0)
        saved.name = "Updated"
        val updated = repository.saveAndFlush(saved)
        assertThat(updated.version).isEqualTo(1)
    }

    @Test
    fun `stale update triggers optimistic lock failure`() {
        val saved = repository.saveAndFlush(newPerson())
        val id = saved.id

        // Detach everything so we work with independent copies, not the same
        // managed instance from the L1 cache.
        entityManager.flush()
        entityManager.clear()

        // Writer A loads a copy (version 0), then detaches it: this is the stale copy.
        val stale = repository.findById(id).get()
        assertThat(stale.version).isEqualTo(0)
        entityManager.detach(stale)

        // Writer B loads a fresh copy, updates it, committing version 0 -> 1 in the DB.
        val fresh = repository.findById(id).get()
        fresh.name = "First"
        repository.saveAndFlush(fresh)
        entityManager.flush()
        entityManager.clear()

        // Writer A now tries to persist its stale copy (still version 0) -> conflict.
        stale.name = "Second"
        assertThatThrownBy {
            repository.saveAndFlush(stale)
            entityManager.flush()
        }.isInstanceOf(OptimisticLockingFailureException::class.java)
    }
}
