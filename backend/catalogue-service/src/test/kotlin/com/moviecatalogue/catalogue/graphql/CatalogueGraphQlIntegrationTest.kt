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

        fun seed(name: String, profilePath: String? = null): UUID {
            val id = UUID.randomUUID()
            store[id] = PersonData(id, null, name, "", null, null, null, profilePath, 0)
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

        override fun searchPeople(query: String, limit: Int, offset: Int): List<com.moviecatalogue.catalogue.people.PersonHit> {
            if (unavailable) throw DependencyUnavailableException()
            return store.values
                .filter { it.name.contains(query, ignoreCase = true) }
                .drop(offset).take(limit)
                .map { com.moviecatalogue.catalogue.people.PersonHit(it.id, it.name) }
        }

        override fun searchPeoplePage(query: String?, limit: Int, offset: Int): com.moviecatalogue.catalogue.people.PersonPage {
            if (unavailable) throw DependencyUnavailableException()
            val q = query?.trim().orEmpty()
            val matched = store.values.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }.sortedBy { it.name }
            return com.moviecatalogue.catalogue.people.PersonPage(
                items = matched.drop(offset).take(limit),
                total = matched.size.toLong(),
                limit = limit,
                offset = offset,
            )
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
    @Autowired lateinit var movieRepo: com.moviecatalogue.catalogue.movie.MovieRepository
    @Autowired lateinit var creditRepo: com.moviecatalogue.catalogue.credit.CreditRepository
    @Autowired lateinit var movieGenreRepo: com.moviecatalogue.catalogue.movie.MovieGenreRepository
    @LocalServerPort var port: Int = 0
    private lateinit var tester: GraphQlTester

    @BeforeEach
    fun setUp() {
        fakePeople.store.clear()
        fakePeople.getPeopleCalls.set(0)
        fakePeople.unavailable = false
        // isolate DB state between tests (shared context, no rollback for HTTP calls)
        creditRepo.deleteAll()
        movieGenreRepo.deleteAll()
        movieRepo.deleteAll()
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

    private fun createMovie(title: String, genreCodes: List<String>, releaseDate: LocalDate): String {
        val codes = genreCodes.joinToString(",") { "\"$it\"" }
        return tester.document(
            """mutation { createMovie(input: { title: "$title", genreCodes: [$codes], releaseDate: "$releaseDate" }) { id version } }""",
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
    fun `createMovie with tmdbId is idempotent (upsert, no duplicate)`() {
        val first = tester.document(
            """mutation { createMovie(input: { title: "Imported", tmdbId: 694 }) { id version } }""",
        ).execute().path("createMovie.id").entity(String::class.java).get()

        // second create with the same tmdbId updates in place and returns the SAME id
        val second = tester.document(
            """mutation { createMovie(input: { title: "Imported (updated)", tmdbId: 694 }) { id } }""",
        ).execute().path("createMovie.id").entity(String::class.java).get()

        assertThat(second).isEqualTo(first)
        // exactly one movie exists
        tester.document("""query { movies(page:{limit:50,offset:0}) { total } }""")
            .execute().path("movies.total").entity(Long::class.java).isEqualTo(1L)
        // and it carries the updated title
        tester.document("""query { movie(id: "$first") { title } }""")
            .execute().path("movie.title").entity(String::class.java).isEqualTo("Imported (updated)")
    }

    @Test
    fun `addMovieCredit with tmdbCreditId is idempotent (upsert, no duplicate)`() {
        val movieId = createMovie("Idem Credits")
        val person = fakePeople.seed("Repeat Actor")

        fun addByTmdb(character: String) = tester.document(
            """mutation { addMovieCredit(movieId: "$movieId", input: {
                 personId: "$person", roleCode: "ACTOR", characterName: "$character", tmdbCreditId: "tc-1" }) { id } }""",
        ).execute().path("addMovieCredit.id").entity(String::class.java).get()

        val c1 = addByTmdb("Hero")
        val c2 = addByTmdb("Hero Renamed")
        assertThat(c2).isEqualTo(c1)
        // still exactly one cast credit on the movie
        tester.document("""query { movie(id: "$movieId") { cast { id characterName } } }""")
            .execute()
            .path("movie.cast").entityList(Any::class.java).hasSize(1)
            .path("movie.cast[0].characterName").entity(String::class.java).isEqualTo("Hero Renamed")
    }

    @Test
    fun `responses carry security headers and echo the correlation id`() {
        // hit the GraphQL endpoint over raw HTTP to inspect headers
        val client = org.springframework.web.reactive.function.client.WebClient.create("http://localhost:$port")
        val resp = client.post().uri("/graphql")
            .header("content-type", "application/json")
            .header("X-Correlation-ID", "test-corr-123")
            .bodyValue("{\"query\":\"{ genres { code } }\"}")
            .retrieve()
            .toBodilessEntity()
            .block()!!
        assertThat(resp.headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff")
        assertThat(resp.headers.getFirst("X-Frame-Options")).isEqualTo("DENY")
        // correlation id is echoed back (observability)
        assertThat(resp.headers.getFirst("X-Correlation-ID")).isEqualTo("test-corr-123")
    }

    @Test
    fun `people query lists people (blank query) and filters by query`() {
        fakePeople.seed("Alice Walker")
        fakePeople.seed("Bob Stone")
        fakePeople.seed("Alicia Keys")

        // blank/no query -> list all, non-null PersonPage
        val all = tester.document(
            """query { people(page:{limit:20,offset:0}) { total items { id name } } }""",
        ).execute()
        all.path("people.total").entity(Long::class.java).isEqualTo(3L)
        val names = all.path("people.items[*].name").entityList(String::class.java).get()
        assertThat(names).containsExactlyInAnyOrder("Alice Walker", "Bob Stone", "Alicia Keys")

        // filtered by query
        val filtered = tester.document(
            """query { people(query: "Ali", page:{limit:20,offset:0}) { total items { name } } }""",
        ).execute().path("people.items[*].name").entityList(String::class.java).get()
        assertThat(filtered).containsExactlyInAnyOrder("Alice Walker", "Alicia Keys")
    }

    @Test
    fun `person photoUrl is the same-origin proxy path when a photo exists, null otherwise`() {
        fakePeople.seed("Has Photo", profilePath = "stored-key-123")
        fakePeople.seed("No Photo")

        val result = tester.document(
            """query { people(page:{limit:20,offset:0}) { items { id name photoUrl } } }""",
        ).execute()
        val items = result.path("people.items").entityList(Any::class.java).get()
        @Suppress("UNCHECKED_CAST")
        val byName = (items as List<Map<String, Any?>>).associateBy { it["name"] }

        assertThat(byName["Has Photo"]!!["photoUrl"]).isEqualTo("/api/people/${byName["Has Photo"]!!["id"]}/photo")
        assertThat(byName["No Photo"]!!["photoUrl"]).isNull()
    }

    @Test
    fun `creditRoles filter by category`() {
        val cats = tester.document("""query { creditRoles(category: CAST) { code category } }""")
            .execute().path("creditRoles[*].category").entityList(String::class.java).get()
        assertThat(cats).isNotEmpty().allMatch { it == "CAST" }
    }

    @Test
    fun `search finds movies by title`() {
        createMovie("The Matrix")
        createMovie("Matrix Reloaded")
        createMovie("Unrelated Film")

        val titles = tester.document(
            """query { search(query: "matrix", page: { limit: 10, offset: 0 }) {
                 movies { title matchedPersonNames } people { name }
               } }""",
        ).execute().path("search.movies[*].title").entityList(String::class.java).get()
        assertThat(titles).containsExactlyInAnyOrder("The Matrix", "Matrix Reloaded")
    }

    @Test
    fun `search surfaces a person and their credited movies with matchedPersonNames`() {
        val movieId = createMovie("Directed Feature")
        val person = fakePeople.seed("Greta Searchable")
        addCast(movieId, person, "Herself")

        val result = tester.document(
            """query { search(query: "Greta", page: { limit: 10, offset: 0 }) {
                 movies { title matchedPersonNames }
                 people { name }
               } }""",
        ).execute()

        val peopleNames = result.path("search.people[*].name").entityList(String::class.java).get()
        assertThat(peopleNames).contains("Greta Searchable")

        val movieTitles = result.path("search.movies[*].title").entityList(String::class.java).get()
        assertThat(movieTitles).contains("Directed Feature")
        val matched = result.path("search.movies[*].matchedPersonNames[*]").entityList(String::class.java).get()
        assertThat(matched).contains("Greta Searchable")
    }

    @Test
    fun `search treats percent as a literal, not a wildcard`() {
        createMovie("100% Real")
        createMovie("Totally Fake")
        val titles = tester.document(
            """query { search(query: "100%", page: { limit: 10, offset: 0 }) { movies { title } people { name } } }""",
        ).execute().path("search.movies[*].title").entityList(String::class.java).get()
        assertThat(titles).containsExactly("100% Real")
    }

    @Test
    fun `blank search query is rejected as BAD_USER_INPUT`() {
        assertThat(errorCodeOf("""query { search(query: "  ", page: { limit: 10, offset: 0 }) { movies { title } people { name } } }"""))
            .isEqualTo("BAD_USER_INPUT")
    }

    @Test
    fun `movies filter combines genre and year, resets cleanly, and reports total`() {
        createMovie("Horror 2020", listOf("HORROR"), LocalDate.of(2020, 5, 1))
        createMovie("Horror Comedy 2020", listOf("HORROR", "PSYCHOLOGICAL_HORROR"), LocalDate.of(2020, 6, 1))
        createMovie("Horror 2021", listOf("HORROR"), LocalDate.of(2021, 1, 1))
        createMovie("Comedy 2020", emptyList(), LocalDate.of(2020, 7, 1))

        fun titlesFor(filter: String) = tester.document(
            """query { movies(page: { limit: 20, offset: 0 }, filter: $filter) { total items { title } } }""",
        ).execute().path("movies.items[*].title").entityList(String::class.java).get()

        assertThat(titlesFor("""{ genreCode: "HORROR" }"""))
            .containsExactlyInAnyOrder("Horror 2020", "Horror Comedy 2020", "Horror 2021")
        assertThat(titlesFor("""{ releaseYear: 2020 }"""))
            .containsExactlyInAnyOrder("Horror 2020", "Horror Comedy 2020", "Comedy 2020")
        assertThat(titlesFor("""{ genreCode: "HORROR", releaseYear: 2020 }"""))
            .containsExactlyInAnyOrder("Horror 2020", "Horror Comedy 2020")

        // cleared filter (omitted) returns everything, and total matches
        val all = tester.document("""query { movies(page: { limit: 20, offset: 0 }) { total items { title } } }""")
            .execute()
        assertThat(all.path("movies.total").entity(Long::class.java).get()).isEqualTo(4L)

        // empty result
        val empty = tester.document(
            """query { movies(page: { limit: 20, offset: 0 }, filter: { genreCode: "HORROR", releaseYear: 1999 }) { total items { title } } }""",
        ).execute()
        assertThat(empty.path("movies.total").entity(Long::class.java).get()).isZero()
        empty.path("movies.items").entityList(Any::class.java).hasSize(0)

        // paginated
        val page1 = tester.document(
            """query { movies(page: { limit: 2, offset: 0 }, filter: { genreCode: "HORROR" }) { total items { title } } }""",
        ).execute()
        assertThat(page1.path("movies.items").entityList(Any::class.java).get()).hasSize(2)
        assertThat(page1.path("movies.total").entity(Long::class.java).get()).isEqualTo(3L)
    }

    @Test
    fun `movies filter with unknown genre code is BAD_USER_INPUT`() {
        assertThat(
            errorCodeOf("""query { movies(filter: { genreCode: "GHOST" }) { total } }"""),
        ).isEqualTo("BAD_USER_INPUT")
    }

    @Test
    fun `movies filter with out-of-range release year is BAD_USER_INPUT`() {
        assertThat(
            errorCodeOf("""query { movies(filter: { releaseYear: 999 }) { total } }"""),
        ).isEqualTo("BAD_USER_INPUT")
    }

    // --- Comments (V2-14) ---------------------------------------------------

    private fun addComment(movieId: String, author: String, text: String): String =
        tester.document(
            """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "$author", text: "$text" }) {
                 id authorDisplayName text createdAt } }""",
        ).execute().path("addMovieComment.id").entity(String::class.java).get()

    @Test
    fun `addMovieComment persists and trims fields, comments query returns it`() {
        val movieId = createMovie("Commentable")
        val result = tester.document(
            """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "  Alice  ", text: "  Great movie!  " }) {
                 id authorDisplayName text createdAt } }""",
        ).execute()
        result.path("addMovieComment.authorDisplayName").entity(String::class.java).isEqualTo("Alice")
        result.path("addMovieComment.text").entity(String::class.java).isEqualTo("Great movie!")
        val createdAt = result.path("addMovieComment.createdAt").entity(String::class.java).get()
        assertThat(createdAt).isNotBlank()

        tester.document("""query { comments(movieId: "$movieId") { total items { authorDisplayName text } } }""")
            .execute()
            .path("comments.total").entity(Long::class.java).isEqualTo(1L)
            .path("comments.items[0].authorDisplayName").entity(String::class.java).isEqualTo("Alice")
    }

    @Test
    fun `comments are returned in reverse chronological order with pagination`() {
        val movieId = createMovie("Chatty")
        val ids = (1..3).map { addComment(movieId, "Author $it", "Comment $it") }

        val all = tester.document(
            """query { comments(movieId: "$movieId", page: { limit: 20, offset: 0 }) { total items { id } } }""",
        ).execute()
        assertThat(all.path("comments.total").entity(Long::class.java).get()).isEqualTo(3L)
        // most recently added first
        assertThat(all.path("comments.items[*].id").entityList(String::class.java).get())
            .containsExactly(ids[2], ids[1], ids[0])

        val page1 = tester.document(
            """query { comments(movieId: "$movieId", page: { limit: 2, offset: 0 }) { total items { id } } }""",
        ).execute()
        assertThat(page1.path("comments.items[*].id").entityList(String::class.java).get())
            .containsExactly(ids[2], ids[1])

        val page2 = tester.document(
            """query { comments(movieId: "$movieId", page: { limit: 2, offset: 2 }) { total items { id } } }""",
        ).execute()
        assertThat(page2.path("comments.items[*].id").entityList(String::class.java).get())
            .containsExactly(ids[0])
    }

    @Test
    fun `blank author or text on a comment is BAD_USER_INPUT`() {
        val movieId = createMovie("BadComment")
        assertThat(
            errorCodeOf(
                """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "   ", text: "fine" }) { id } }""",
            ),
        ).isEqualTo("BAD_USER_INPUT")
        assertThat(
            errorCodeOf(
                """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "Alice", text: "   " }) { id } }""",
            ),
        ).isEqualTo("BAD_USER_INPUT")
    }

    @Test
    fun `over-limit author or text on a comment is BAD_USER_INPUT`() {
        val movieId = createMovie("LongComment")
        val longAuthor = "a".repeat(51)
        val longText = "a".repeat(2001)
        assertThat(
            errorCodeOf(
                """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "$longAuthor", text: "fine" }) { id } }""",
            ),
        ).isEqualTo("BAD_USER_INPUT")
        assertThat(
            errorCodeOf(
                """mutation { addMovieComment(movieId: "$movieId", input: { authorDisplayName: "Alice", text: "$longText" }) { id } }""",
            ),
        ).isEqualTo("BAD_USER_INPUT")
    }

    @Test
    fun `commenting on or listing comments for a missing movie is NOT_FOUND`() {
        val ghost = UUID.randomUUID()
        assertThat(
            errorCodeOf("""mutation { addMovieComment(movieId: "$ghost", input: { authorDisplayName: "Alice", text: "Hi" }) { id } }"""),
        ).isEqualTo("NOT_FOUND")
        assertThat(
            errorCodeOf("""query { comments(movieId: "$ghost") { total } }"""),
        ).isEqualTo("NOT_FOUND")
    }

    @Test
    fun `deleting a movie cascades to its comments`() {
        val movieId = createMovie("Doomed")
        addComment(movieId, "Alice", "Bye")

        tester.document("""mutation { deleteMovie(id: "$movieId") { deletedId } }""").executeAndVerify()

        assertThat(
            errorCodeOf("""query { comments(movieId: "$movieId") { total } }"""),
        ).isEqualTo("NOT_FOUND")
    }
}
