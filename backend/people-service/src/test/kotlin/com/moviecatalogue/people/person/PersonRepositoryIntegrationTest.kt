package com.moviecatalogue.people.person

import com.moviecatalogue.people.common.UuidV7
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
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
        // simulate a stale copy at the original version
        val stale = repository.findById(saved.id).get()
        // first writer wins
        saved.name = "First"
        repository.saveAndFlush(saved)
        // second writer with old version
        stale.version = 0
        stale.name = "Second"
        assertThatThrownBy { repository.saveAndFlush(stale) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }
}
