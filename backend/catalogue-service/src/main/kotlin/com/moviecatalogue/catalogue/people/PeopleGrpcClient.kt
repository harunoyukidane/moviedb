package com.moviecatalogue.catalogue.people

import com.moviecatalogue.catalogue.domain.ConflictException
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.domain.NotFoundException
import com.moviecatalogue.catalogue.domain.ValidationException
import com.google.protobuf.FieldMask
import com.moviecatalogue.people.v1.CreatePersonRequest
import com.moviecatalogue.people.v1.DeletePersonRequest
import com.moviecatalogue.people.v1.GetPeopleRequest
import com.moviecatalogue.people.v1.GetPersonRequest
import com.moviecatalogue.people.v1.PeopleServiceGrpc
import com.moviecatalogue.people.v1.PersonPatch
import com.moviecatalogue.people.v1.PersonResponse
import com.moviecatalogue.people.v1.SearchPeopleRequest
import com.moviecatalogue.people.v1.UpdatePersonRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * gRPC adapter for the People Service (§6.5/§13). Applies a per-call deadline
 * (~1s reads, ~2s writes) and maps gRPC Status to catalogue domain exceptions:
 * UNAVAILABLE/DEADLINE_EXCEEDED -> DependencyUnavailable, NOT_FOUND -> NotFound,
 * ALREADY_EXISTS/ABORTED -> Conflict, INVALID_ARGUMENT/FAILED_PRECONDITION -> Validation.
 */
@Component
class PeopleGrpcClient(
    @GrpcClient("people-service")
    private val stub: PeopleServiceGrpc.PeopleServiceBlockingStub,
) : PeopleClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    private val readDeadlineMs = 1_000L
    private val writeDeadlineMs = 2_000L

    private fun read() = stub.withDeadlineAfter(readDeadlineMs, TimeUnit.MILLISECONDS)
    private fun write() = stub.withDeadlineAfter(writeDeadlineMs, TimeUnit.MILLISECONDS)

    override fun getPerson(id: UUID): PersonData = call {
        read().getPerson(GetPersonRequest.newBuilder().setId(id.toString()).build()).toData()
    }

    override fun getPeople(ids: Collection<UUID>): Map<UUID, PersonData> {
        if (ids.isEmpty()) return emptyMap()
        return call {
            val request = GetPeopleRequest.newBuilder()
                .addAllIds(ids.map { it.toString() })
                .build()
            read().getPeople(request).peopleList.associate {
                val data = it.toData()
                data.id to data
            }
        }
    }

    override fun searchPeople(query: String, limit: Int, offset: Int): List<PersonHit> = call {
        val request = SearchPeopleRequest.newBuilder()
            .setQuery(query)
            .setLimit(limit)
            .setOffset(offset)
            .build()
        read().searchPeople(request).peopleList.map { PersonHit(UUID.fromString(it.id), it.name) }
    }

    override fun createPerson(command: CreatePersonData): PersonData = call {
        val b = CreatePersonRequest.newBuilder()
            .setName(command.name)
            .setBiography(command.biography)
        command.birthDate?.let { b.birthDate = it.format(iso) }
        command.deathDate?.let { b.deathDate = it.format(iso) }
        command.placeOfBirth?.let { b.placeOfBirth = it }
        write().createPerson(b.build()).toData()
    }

    override fun updatePerson(command: UpdatePersonData): PersonData = call {
        val patch = PersonPatch.newBuilder()
        command.name?.let { patch.name = it }
        command.biography?.let { patch.biography = it }
        command.birthDate?.let { patch.birthDate = it.format(iso) }
        command.deathDate?.let { patch.deathDate = it.format(iso) }
        command.placeOfBirth?.let { patch.placeOfBirth = it }
        val request = UpdatePersonRequest.newBuilder()
            .setId(command.id.toString())
            .setExpectedVersion(command.expectedVersion)
            .setPatch(patch.build())
            .setUpdateMask(FieldMask.newBuilder().addAllPaths(command.maskPaths).build())
            .build()
        write().updatePerson(request).toData()
    }

    override fun deletePerson(id: UUID) {
        call { write().deletePerson(DeletePersonRequest.newBuilder().setId(id.toString()).build()) }
    }

    /** Executes a gRPC call, translating StatusRuntimeException into domain exceptions. */
    private fun <T> call(block: () -> T): T {
        return try {
            block()
        } catch (e: StatusRuntimeException) {
            throw e.toDomain()
        }
    }

    private fun StatusRuntimeException.toDomain(): RuntimeException = when (status.code) {
        Status.Code.NOT_FOUND -> NotFoundException(status.description ?: "person not found")
        Status.Code.ALREADY_EXISTS -> ConflictException(status.description ?: "person already exists")
        Status.Code.ABORTED -> ConflictException(status.description ?: "person was modified concurrently")
        Status.Code.INVALID_ARGUMENT, Status.Code.FAILED_PRECONDITION ->
            ValidationException(status.description ?: "invalid person request")
        Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED -> {
            log.warn("People service unavailable: {} {}", status.code, status.description)
            DependencyUnavailableException(cause = this)
        }
        else -> {
            log.warn("Unexpected People gRPC status: {} {}", status.code, status.description)
            DependencyUnavailableException(cause = this)
        }
    }

    private fun PersonResponse.toData(): PersonData = PersonData(
        id = UUID.fromString(id),
        tmdbId = if (hasTmdbId()) tmdbId else null,
        name = name,
        biography = biography,
        birthDate = if (hasBirthDate()) LocalDate.parse(birthDate, iso) else null,
        deathDate = if (hasDeathDate()) LocalDate.parse(deathDate, iso) else null,
        placeOfBirth = if (hasPlaceOfBirth()) placeOfBirth else null,
        profilePath = if (hasProfilePath()) profilePath else null,
        version = version,
    )
}
