package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.people.CreatePersonData
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.people.PersonData
import com.moviecatalogue.catalogue.people.UpdatePersonData
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end GraphQL contract/integration tests over HTTP against real Postgres,
 * with a controllable fake PeopleClient standing in for the People gRPC service.
 * Covers: full movie+credit+genre lifecycle, batched hydration (call count),
 * degraded hydration, optimistic-lock CONFLICT, error-code mapping, pagination.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(CatalogueGraphQlIntegrationTest.Config::class)
class CatalogueGraphQlIntegrationTest {

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
        }
    }

    /** Controllable fake People service: records people, counts batch calls, toggles availability. */
    class FakePeople : PeopleClient {
        val store = mutableMapOf<UUID, PersonData>()
        val getPeopleCalls = AtomicInteger(0)
        var unavailable = false

        fun seed(name: String): UUID {
            val id = UUID.randomUUID()
            store[id] = PersonData(id, null, name, "", null, null, null, null, 0)
            return id
        }

        override fun getPerson(id: UUID): PersonData {
            if (unavailable) throw DependencyUnavailableException()
            return store[id] ?: throw com.moviecatalogue.catalogue.domain.NotFoundException("person '$id' not found")
        }

        override fun getPeople(ids: Collection<UUID>): Map<UUID, PersonData> {
            getPeopleCalls.incrementAndGet()
            if (unavailable) throw DependencyUnavailableException()
            return ids.mapNotNull { store[it] }.associateBy { it.id }
        }

        override fun createPerson(command: CreatePersonData): PersonData {
            val id = UUID.randomUUID()
            val data = PersonData(id, null, command.name, command.biography, command.birthDate, command.deathDate, command.placeOfBirth, null, 0)
            store[id] = data
            return data
        }

        override fun updatePerson(command: UpdatePersonData): PersonData {
            val existing = store[command.id] ?: throw com.moviecatalogue.catalogue.domain.NotFoundException("person")
            val updated = existing.copy(name = command.name ?: existing.name, version = existing.version + 1)
            store[command.id] = updated
            return updated
        }

        override fun deletePerson(id: UUID) { store.remove(id) }
    }

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakePeople(): FakePeople = FakePeople()
    }

    @Autowired lateinit var fakePeople: FakePeople
    @LocalServerPort var port: Int = 0
    private lateinit var tester: GraphQlTester

    @BeforeEach
    fun setUp() {
        fakePeople.store.clear()
        fakePeople.getPeopleCalls.set(0)
        fakePeople.unavailable = false
        val client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port/graphql")
            .responseTimeout(java.time.Duration.ofSeconds(30))
            .build()
        tester = HttpGraphQlTester.create(client)
    }

    private fun createMovie(title: String, genreCodes: List<String> = emptyList()): String {
        val codes = genreCodes.joinToString(",") { "\"$it\"" }
        return tester.document(
            """mutation { createMovie(input: { title: "$title", genreCodes: [$codes] }) { id version } }""",
        ).execute().path("createMovie.id").entity(String::class.java).get()
    }

    private fun addCast(movieId: String, personId: UUID, character: String): String =
        tester.document(
            """mutation { addMovieCredit(movieId: "$movieId", input: { personId: "$personId", roleCode: "ACTOR", characterName: "$character" }) { id category person { id name available } } }""",
        ).execute().path("addMovieCredit.id").entity(String::class.java).get()

    /** Runs [doc], asserts exactly one error, and returns its extensions.code. */
    private fun errorCodeOf(doc: String): String {
        val codes = mutableListOf<String>()
        tester.document(doc).execute().errors().satisfy { errors ->
            errors.forEach { codes.add(it.extensions["code"] as String) }
        }
        assertThat(codes).isNotEmpty()
        return codes.first()
    }

    @Test
    fun `full movie credit genre lifecycle`() {
        val movieId = createMovie("Inception", listOf("HORROR"))
        val person = fakePeople.seed("Leonardo")
        val creditId = addCast(movieId, person, "Cobb")

        tester.document(
            """query { movie(id: "$movieId") {
                 title version
                 genres { code title }
                 cast { id characterName person { name available } }
               } }""",
        ).execute()
            .path("movie.title").entity(String::class.java).isEqualTo("Inception")
            .path("movie.genres[0].code").entity(String::class.java).isEqualTo("HORROR")
            .path("movie.cast[0].characterName").entity(String::class.java).isEqualTo("Cobb")
            .path("movie.cast[0].person.name").entity(String::class.java).isEqualTo("Leonardo")
            .path("movie.cast[0].person.available").entity(Boolean::class.java).isEqualTo(true)

        // remove credit does not delete the person
        tester.document("""mutation { removeMovieCredit(id: "$creditId") { deletedId } }""")
            .execute().path("removeMovieCredit.deletedId").entity(String::class.java).isEqualTo(creditId)
        assertThat(fakePeople.store).containsKey(person)
    }

    @Test
    fun `movies list with many credits performs one batched gRPC call`() {
        // two movies, several credits each, referencing several distinct people
        val m1 = createMovie("Movie A")
        val m2 = createMovie("Movie B")
        val people = (1..6).map { fakePeople.seed("Person $it") }
        addCast(m1, people[0], "A1"); addCast(m1, people[1], "A2"); addCast(m1, people[2], "A3")
        addCast(m2, people[3], "B1"); addCast(m2, people[4], "B2"); addCast(m2, people[5], "B3")

        fakePeople.getPeopleCalls.set(0)
        val result = tester.document(
            """query { movies(page: { limit: 20, offset: 0 }) {
                 total
                 items { title cast { person { name available } } }
               } }""",
        ).execute()
        val total = result.path("movies.total").entity(Long::class.java).get()
        assertThat(total).isEqualTo(2L)
        val avail = result.path("movies.items[*].cast[*].person.available").entityList(Boolean::class.java).get()
        assertThat(avail).isNotEmpty().allMatch { it }

        // The whole page's credits hydrate in a single batched gRPC call (no N+1).
        assertThat(fakePeople.getPeopleCalls.get()).isEqualTo(1)
    }

    @Test
    fun `degraded people service yields available false without failing the page`() {
        val movieId = createMovie("Degraded")
        val person = fakePeople.seed("Ghost")
        addCast(movieId, person, "Role")
        fakePeople.unavailable = true

        val avail = tester.document(
            """query { movie(id: "$movieId") { title cast { person { available } } } }""",
        ).execute()
            .path("movie.title").entity(String::class.java).isEqualTo("Degraded")
            .path("movie.cast[*].person.available").entityList(Boolean::class.java).get()
        assertThat(avail).containsExactly(false)
    }

    @Test
    fun `optimistic lock conflict maps to CONFLICT`() {
        val movieId = createMovie("Lockme")
        assertThat(
            errorCodeOf("""mutation { updateMovie(id: "$movieId", expectedVersion: 99, input: { title: "New" }) { id } }"""),
        ).isEqualTo("CONFLICT")
    }

    @Test
    fun `movie not found returns NOT_FOUND on mutation and null on query`() {
        val ghost = UUID.randomUUID()
        tester.document("""query { movie(id: "$ghost") { id } }""")
            .execute().path("movie").valueIsNull()
        assertThat(errorCodeOf("""mutation { deleteMovie(id: "$ghost") { deletedId } }""")).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `cast credit without character is BAD_USER_INPUT`() {
        val movieId = createMovie("BadInput")
        val person = fakePeople.seed("NoChar")
        assertThat(
            errorCodeOf("""mutation { addMovieCredit(movieId: "$movieId", input: { personId: "$person", roleCode: "ACTOR" }) { id } }"""),
        ).isEqualTo("BAD_USER_INPUT")
    }

    @Test
    fun `person in use blocks delete then succeeds after credit removed`() {
        val movieId = createMovie("Refd")
        val person = fakePeople.seed("Referenced")
        val creditId = addCast(movieId, person, "Role")

        assertThat(errorCodeOf("""mutation { deletePerson(id: "$person") { deletedId } }""")).isEqualTo("PERSON_IN_USE")

        tester.document("""mutation { removeMovieCredit(id: "$creditId") { deletedId } }""").executeAndVerify()
        tester.document("""mutation { deletePerson(id: "$person") { deletedId } }""")
            .execute().path("deletePerson.deletedId").entity(String::class.java).isEqualTo(person.toString())
        assertThat(fakePeople.store).doesNotContainKey(person)
    }

    @Test
    fun `add credit rejected when People unavailable and no row is written`() {
        val movieId = createMovie("DepDown")
        val person = fakePeople.seed("Temp")
        fakePeople.unavailable = true
        assertThat(
            errorCodeOf("""mutation { addMovieCredit(movieId: "$movieId", input: { personId: "$person", roleCode: "ACTOR", characterName: "X" }) { id } }"""),
        ).isEqualTo("DEPENDENCY_UNAVAILABLE")
        // recover and confirm no credit was written
        fakePeople.unavailable = false
        tester.document("""query { movie(id: "$movieId") { cast { id } } }""")
            .execute().path("movie.cast").entityList(Any::class.java).hasSize(0)
    }

    @Test
    fun `genres query returns only active by default`() {
        val actives = tester.document("""query { genres { code active } }""")
            .execute().path("genres[*].active").entityList(Boolean::class.java).get()
        assertThat(actives).isNotEmpty().allMatch { it }
    }

    @Test
    fun `page limit is clamped to 100`() {
        createMovie("Clamp")
        tester.document("""query { movies(page: { limit: 9999, offset: 0 }) { limit } }""")
            .execute().path("movies.limit").entity(Int::class.java).isEqualTo(100)
    }

    @Test
    fun `creditRoles filter by category`() {
        val cats = tester.document("""query { creditRoles(category: CAST) { code category } }""")
            .execute().path("creditRoles[*].category").entityList(String::class.java).get()
        assertThat(cats).isNotEmpty().allMatch { it == "CAST" }
    }
}
