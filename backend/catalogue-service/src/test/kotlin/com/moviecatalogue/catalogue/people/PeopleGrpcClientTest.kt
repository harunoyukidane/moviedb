package com.moviecatalogue.catalogue.people

import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.people.v1.GetPeopleRequest
import com.moviecatalogue.people.v1.GetPeopleResponse
import com.moviecatalogue.people.v1.GetPersonRequest
import com.moviecatalogue.people.v1.PeopleServiceGrpc
import com.moviecatalogue.people.v1.PersonResponse
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Verifies the gRPC adapter's deadline handling and Status->domain-exception
 * mapping using an in-process fake People server whose behaviour is programmable
 * per test.
 */
class PeopleGrpcClientTest {

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    /** A People server whose responses each test configures. */
    private class FakePeople(
        var onGetPerson: (GetPersonRequest, StreamObserver<PersonResponse>) -> Unit = { _, o -> o.onError(Status.UNIMPLEMENTED.asRuntimeException()) },
        var onGetPeople: (GetPeopleRequest, StreamObserver<GetPeopleResponse>) -> Unit = { _, o -> o.onNext(GetPeopleResponse.getDefaultInstance()); o.onCompleted() },
        var onSearchPeople: (com.moviecatalogue.people.v1.SearchPeopleRequest, StreamObserver<com.moviecatalogue.people.v1.SearchPeopleResponse>) -> Unit =
            { _, o -> o.onNext(com.moviecatalogue.people.v1.SearchPeopleResponse.getDefaultInstance()); o.onCompleted() },
    ) : PeopleServiceGrpc.PeopleServiceImplBase() {
        override fun getPerson(request: GetPersonRequest, obs: StreamObserver<PersonResponse>) = onGetPerson(request, obs)
        override fun getPeople(request: GetPeopleRequest, obs: StreamObserver<GetPeopleResponse>) = onGetPeople(request, obs)
        override fun searchPeople(request: com.moviecatalogue.people.v1.SearchPeopleRequest, obs: StreamObserver<com.moviecatalogue.people.v1.SearchPeopleResponse>) = onSearchPeople(request, obs)
    }

    private fun start(fake: FakePeople): PeopleGrpcClient {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fake).build().start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return PeopleGrpcClient(PeopleServiceGrpc.newBlockingStub(channel))
    }

    /** For deadline tests we need a real executor (directExecutor can't expire deadlines mid-call). */
    private fun startThreaded(fake: FakePeople): PeopleGrpcClient {
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name).addService(fake).build().start()
        channel = InProcessChannelBuilder.forName(name).build()
        return PeopleGrpcClient(PeopleServiceGrpc.newBlockingStub(channel))
    }

    @AfterEach
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        if (::server.isInitialized) server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
    }

    @Test
    fun `getPerson maps NOT_FOUND to NotFoundException`() {
        val client = start(FakePeople(onGetPerson = { _, o -> o.onError(Status.NOT_FOUND.asRuntimeException()) }))
        assertThatThrownBy { client.getPerson(UUID.randomUUID()) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `getPerson maps UNAVAILABLE to DependencyUnavailable`() {
        val client = start(FakePeople(onGetPerson = { _, o -> o.onError(Status.UNAVAILABLE.asRuntimeException()) }))
        assertThatThrownBy { client.getPerson(UUID.randomUUID()) }
            .isInstanceOf(DependencyUnavailableException::class.java)
    }

    @Test
    fun `getPerson maps ABORTED to Conflict and INVALID_ARGUMENT to Validation`() {
        val aborted = start(FakePeople(onGetPerson = { _, o -> o.onError(Status.ABORTED.asRuntimeException()) }))
        assertThatThrownBy { aborted.getPerson(UUID.randomUUID()) }.isInstanceOf(ConflictException::class.java)
        tearDown()

        val invalid = start(FakePeople(onGetPerson = { _, o -> o.onError(Status.INVALID_ARGUMENT.asRuntimeException()) }))
        assertThatThrownBy { invalid.getPerson(UUID.randomUUID()) }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `getPeople returns results keyed by id and never fails on empty input`() {
        val a = UUID.randomUUID()
        val client = start(FakePeople(onGetPeople = { _, o ->
            o.onNext(
                GetPeopleResponse.newBuilder()
                    .addPeople(PersonResponse.newBuilder().setId(a.toString()).setName("A").setBiography("").setVersion(0).build())
                    .build(),
            )
            o.onCompleted()
        }))
        assertThat(client.getPeople(emptyList())).isEmpty()
        val result = client.getPeople(listOf(a))
        assertThat(result).containsKey(a)
        assertThat(result[a]!!.name).isEqualTo("A")
    }

    @Test
    fun `searchPeople returns hits mapped from the gRPC response`() {
        val id = UUID.randomUUID()
        val client = start(FakePeople(onSearchPeople = { _, o ->
            o.onNext(
                com.moviecatalogue.people.v1.SearchPeopleResponse.newBuilder()
                    .addPeople(PersonResponse.newBuilder().setId(id.toString()).setName("Nolan").setBiography("").setVersion(0).build())
                    .setTotal(1)
                    .build(),
            )
            o.onCompleted()
        }))
        val hits = client.searchPeople("nol", 10, 0)
        assertThat(hits).hasSize(1)
        assertThat(hits[0].id).isEqualTo(id)
        assertThat(hits[0].name).isEqualTo("Nolan")
    }

    @Test
    fun `deadline exceeded maps to DependencyUnavailable`() {
        // Server never responds; the 1s read deadline should fire and map to UNAVAILABLE-class.
        val client = startThreaded(FakePeople(onGetPerson = { _, _ -> /* hang: never onNext/onCompleted */ }))
        assertThatThrownBy { client.getPerson(UUID.randomUUID()) }
            .isInstanceOf(DependencyUnavailableException::class.java)
    }
}
