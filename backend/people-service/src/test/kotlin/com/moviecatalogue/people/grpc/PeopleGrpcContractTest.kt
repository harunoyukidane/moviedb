package com.moviecatalogue.people.grpc

import com.moviecatalogue.people.application.PeopleApplicationService
import com.moviecatalogue.people.application.PersonView
import com.moviecatalogue.people.application.SearchPeopleCommand
import com.moviecatalogue.people.application.SearchPeopleResult
import com.moviecatalogue.people.application.UpdatePersonCommand
import com.moviecatalogue.people.domain.DuplicateTmdbIdException
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.PreconditionException
import com.moviecatalogue.people.domain.ValidationException
import com.moviecatalogue.people.domain.VersionConflictException
import com.google.protobuf.FieldMask
import com.moviecatalogue.people.v1.CreatePersonRequest
import com.moviecatalogue.people.v1.DeletePersonRequest
import com.moviecatalogue.people.v1.GetPeopleRequest
import com.moviecatalogue.people.v1.GetPersonRequest
import com.moviecatalogue.people.v1.PeopleServiceGrpc
import com.moviecatalogue.people.v1.PersonPatch
import com.moviecatalogue.people.v1.SearchPeopleRequest
import com.moviecatalogue.people.v1.UpdatePersonRequest
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * In-process gRPC contract tests: the real adapter is served on an in-process
 * channel and exercised via the generated blocking stub, verifying proto shapes
 * and the deliberate status-code mapping. The application service is mocked so
 * these stay fast and free of a database.
 */
class PeopleGrpcContractTest {

    private val app = mockk<PeopleApplicationService>()
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: PeopleServiceGrpc.PeopleServiceBlockingStub

    private fun view(
        id: UUID = UUID.randomUUID(),
        name: String = "Jane",
        version: Long = 0,
        tmdbId: Long? = null,
        birthDate: LocalDate? = null,
        deathDate: LocalDate? = null,
    ) = PersonView(id, tmdbId, name, "bio", birthDate, deathDate, null, null, version)

    @BeforeEach
    fun setUp() {
        val serverName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(PeopleGrpcService(app))
            .build()
            .start()
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
        stub = PeopleServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun tearDown() {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
    }

    private fun statusOf(block: () -> Unit): Status.Code =
        catchThrowableOfType({ block() }, StatusRuntimeException::class.java).status.code

    // --- GetPerson ---------------------------------------------------------

    @Test
    fun `getPerson returns the proto shape`() {
        val id = UUID.randomUUID()
        every { app.getPerson(id) } returns view(id = id, name = "Ada", version = 2, tmdbId = 99, deathDate = LocalDate.of(1852, 11, 27))
        val resp = stub.getPerson(GetPersonRequest.newBuilder().setId(id.toString()).build())
        assertThat(resp.id).isEqualTo(id.toString())
        assertThat(resp.name).isEqualTo("Ada")
        assertThat(resp.version).isEqualTo(2)
        assertThat(resp.hasTmdbId()).isTrue()
        assertThat(resp.tmdbId).isEqualTo(99)
        assertThat(resp.hasDeathDate()).isTrue()
        assertThat(resp.deathDate).isEqualTo("1852-11-27")
    }

    @Test
    fun `getPerson invalid uuid maps to INVALID_ARGUMENT`() {
        assertThat(statusOf {
            stub.getPerson(GetPersonRequest.newBuilder().setId("not-a-uuid").build())
        }).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    @Test
    fun `getPerson not found maps to NOT_FOUND`() {
        val id = UUID.randomUUID()
        every { app.getPerson(id) } throws PersonNotFoundException()
        assertThat(statusOf {
            stub.getPerson(GetPersonRequest.newBuilder().setId(id.toString()).build())
        }).isEqualTo(Status.Code.NOT_FOUND)
    }

    // --- GetPeople ---------------------------------------------------------

    @Test
    fun `getPeople returns keyed results in distinct request order, missing absent`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val missing = UUID.randomUUID()
        every { app.getPeople(any()) } returns mapOf(a to view(id = a, name = "A"), b to view(id = b, name = "B"))
        val resp = stub.getPeople(
            GetPeopleRequest.newBuilder().addIds(a.toString()).addIds(missing.toString()).addIds(b.toString()).addIds(a.toString()).build(),
        )
        // a, b present; missing absent; order follows distinct request order (a, then b)
        assertThat(resp.peopleList.map { it.id }).containsExactly(a.toString(), b.toString())
    }

    @Test
    fun `getPeople oversize batch maps to INVALID_ARGUMENT`() {
        every { app.getPeople(any()) } throws ValidationException("too many")
        val req = GetPeopleRequest.newBuilder().apply {
            repeat(201) { addIds(UUID.randomUUID().toString()) }
        }.build()
        assertThat(statusOf { stub.getPeople(req) }).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    // --- SearchPeople ------------------------------------------------------

    @Test
    fun `searchPeople returns people and total`() {
        every { app.searchPeople(any()) } returns SearchPeopleResult(listOf(view(name = "Nolan")), 1)
        val resp = stub.searchPeople(
            SearchPeopleRequest.newBuilder().setQuery("nol").setLimit(10).setOffset(0).build(),
        )
        assertThat(resp.total).isEqualTo(1)
        assertThat(resp.peopleList).hasSize(1)
        assertThat(resp.peopleList[0].name).isEqualTo("Nolan")
    }

    @Test
    fun `searchPeople blank query maps to INVALID_ARGUMENT`() {
        every { app.searchPeople(SearchPeopleCommand("", 10, 0)) } throws ValidationException("blank")
        assertThat(statusOf {
            stub.searchPeople(SearchPeopleRequest.newBuilder().setQuery("").setLimit(10).build())
        }).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    // --- CreatePerson ------------------------------------------------------

    @Test
    fun `createPerson deceased round-trips death_date`() {
        val id = UUID.randomUUID()
        every { app.createPerson(any()) } returns view(
            id = id, name = "Bela", birthDate = LocalDate.of(1882, 10, 20), deathDate = LocalDate.of(1956, 8, 16),
        )
        val resp = stub.createPerson(
            CreatePersonRequest.newBuilder()
                .setName("Bela").setBiography("actor")
                .setBirthDate("1882-10-20").setDeathDate("1956-08-16")
                .build(),
        )
        assertThat(resp.id).isEqualTo(id.toString())
        assertThat(resp.deathDate).isEqualTo("1956-08-16")
    }

    @Test
    fun `createPerson duplicate tmdb maps to ALREADY_EXISTS`() {
        every { app.createPerson(any()) } throws DuplicateTmdbIdException(7)
        assertThat(statusOf {
            stub.createPerson(CreatePersonRequest.newBuilder().setName("Dup").setTmdbId(7).build())
        }).isEqualTo(Status.Code.ALREADY_EXISTS)
    }

    @Test
    fun `createPerson bad date maps to INVALID_ARGUMENT`() {
        // parsing happens in the adapter before the app call
        assertThat(statusOf {
            stub.createPerson(CreatePersonRequest.newBuilder().setName("X").setBirthDate("31-12-1999").build())
        }).isEqualTo(Status.Code.INVALID_ARGUMENT)
    }

    // --- UpdatePerson ------------------------------------------------------

    @Test
    fun `updatePerson applies mask and returns updated`() {
        val id = UUID.randomUUID()
        val cmd = slotCapture()
        every { app.updatePerson(any()) } answers {
            cmd.value = firstArg()
            view(id = id, name = "New", version = 6)
        }
        val resp = stub.updatePerson(
            UpdatePersonRequest.newBuilder()
                .setId(id.toString())
                .setExpectedVersion(5)
                .setPatch(PersonPatch.newBuilder().setName("New").build())
                .setUpdateMask(FieldMask.newBuilder().addPaths("name").build())
                .build(),
        )
        assertThat(resp.name).isEqualTo("New")
        assertThat(resp.version).isEqualTo(6)
        assertThat(cmd.value.maskPaths).containsExactly("name")
        assertThat(cmd.value.expectedVersion).isEqualTo(5)
    }

    @Test
    fun `updatePerson stale version maps to ABORTED`() {
        every { app.updatePerson(any()) } throws VersionConflictException()
        assertThat(statusOf {
            stub.updatePerson(
                UpdatePersonRequest.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setExpectedVersion(1)
                    .setPatch(PersonPatch.newBuilder().setName("X").build())
                    .setUpdateMask(FieldMask.newBuilder().addPaths("name").build())
                    .build(),
            )
        }).isEqualTo(Status.Code.ABORTED)
    }

    @Test
    fun `updatePerson unknown mask maps to FAILED_PRECONDITION`() {
        every { app.updatePerson(any()) } throws PreconditionException("bad mask")
        assertThat(statusOf {
            stub.updatePerson(
                UpdatePersonRequest.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setExpectedVersion(0)
                    .setPatch(PersonPatch.newBuilder().build())
                    .setUpdateMask(FieldMask.newBuilder().addPaths("bogus").build())
                    .build(),
            )
        }).isEqualTo(Status.Code.FAILED_PRECONDITION)
    }

    // --- DeletePerson ------------------------------------------------------

    @Test
    fun `deletePerson returns empty and calls app`() {
        val id = UUID.randomUUID()
        every { app.deletePerson(id) } returns Unit
        stub.deletePerson(DeletePersonRequest.newBuilder().setId(id.toString()).build())
        verify { app.deletePerson(id) }
    }

    @Test
    fun `deletePerson not found maps to NOT_FOUND`() {
        val id = UUID.randomUUID()
        every { app.deletePerson(id) } throws PersonNotFoundException()
        assertThat(statusOf {
            stub.deletePerson(DeletePersonRequest.newBuilder().setId(id.toString()).build())
        }).isEqualTo(Status.Code.NOT_FOUND)
    }

    // --- ListCountries (V2.2-10) --------------------------------------------

    @Test
    fun `listCountries defaults to active only and maps every field`() {
        every { app.listCountries(true) } returns listOf(
            com.moviecatalogue.people.reference.CountryCode(code = "US", name = "United States", active = true, displayOrder = 10),
        )
        val resp = stub.listCountries(com.moviecatalogue.people.v1.ListCountriesRequest.newBuilder().build())
        assertThat(resp.countriesList).hasSize(1)
        assertThat(resp.countriesList[0].code).isEqualTo("US")
        assertThat(resp.countriesList[0].name).isEqualTo("United States")
        assertThat(resp.countriesList[0].active).isTrue()
        assertThat(resp.countriesList[0].displayOrder).isEqualTo(10)
    }

    @Test
    fun `listCountries with activeOnly false includes inactive`() {
        every { app.listCountries(false) } returns listOf(
            com.moviecatalogue.people.reference.CountryCode(code = "ZZ", name = "Retired", active = false, displayOrder = 30),
        )
        val resp = stub.listCountries(
            com.moviecatalogue.people.v1.ListCountriesRequest.newBuilder().setActiveOnly(false).build(),
        )
        assertThat(resp.countriesList.single().active).isFalse()
    }

    // small mutable holder to capture a command from the mock answer
    private class Holder { lateinit var value: UpdatePersonCommand }
    private fun slotCapture() = Holder()
}
