package com.moviecatalogue.people.grpc

import com.google.protobuf.Empty
import io.grpc.Metadata
import com.moviecatalogue.people.application.CreatePersonCommand
import com.moviecatalogue.people.application.PeopleApplicationService
import com.moviecatalogue.people.application.PersonView
import com.moviecatalogue.people.application.SearchPeopleCommand
import com.moviecatalogue.people.application.UpdatePersonCommand
import com.moviecatalogue.people.domain.DateHelpers
import com.moviecatalogue.people.domain.DuplicateTmdbIdException
import com.moviecatalogue.people.domain.PersonNotFoundException
import com.moviecatalogue.people.domain.PersonRules
import com.moviecatalogue.people.domain.PreconditionException
import com.moviecatalogue.people.domain.ValidationException
import com.moviecatalogue.people.domain.VersionConflictException
import com.moviecatalogue.people.v1.CreatePersonRequest
import com.moviecatalogue.people.v1.DeletePersonRequest
import com.moviecatalogue.people.v1.GetPeopleRequest
import com.moviecatalogue.people.v1.GetPeopleResponse
import com.moviecatalogue.people.v1.GetPersonRequest
import com.moviecatalogue.people.v1.PeopleServiceGrpc
import com.moviecatalogue.people.v1.PersonResponse
import com.moviecatalogue.people.v1.SearchPeopleRequest
import com.moviecatalogue.people.v1.SearchPeopleResponse
import com.moviecatalogue.people.v1.UpdatePersonRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.transaction.CannotCreateTransactionException
import java.util.UUID

/**
 * Inbound gRPC adapter (§6.4). Maps proto <-> application commands and translates
 * domain exceptions to deliberate gRPC status codes (§8.3, phase-2 task 4):
 * validation -> INVALID_ARGUMENT, not found -> NOT_FOUND, duplicate tmdb_id ->
 * ALREADY_EXISTS, stale version -> ABORTED, bad field mask -> FAILED_PRECONDITION,
 * DB down -> UNAVAILABLE.
 */
@GrpcService
class PeopleGrpcService(
    private val app: PeopleApplicationService,
) : PeopleServiceGrpc.PeopleServiceImplBase() {

    override fun getPerson(request: GetPersonRequest, responseObserver: StreamObserver<PersonResponse>) {
        handle(responseObserver) {
            val id = parseId(request.id)
            app.getPerson(id).toProto()
        }
    }

    override fun getPeople(request: GetPeopleRequest, responseObserver: StreamObserver<GetPeopleResponse>) {
        handle(responseObserver) {
            val ids = request.idsList.map { parseId(it) }
            val byId = app.getPeople(ids)
            val builder = GetPeopleResponse.newBuilder()
            // Return in the (de-duplicated) request order; missing IDs are simply absent.
            for (id in ids.distinct()) {
                byId[id]?.let { builder.addPeople(it.toProto()) }
            }
            builder.build()
        }
    }

    override fun searchPeople(request: SearchPeopleRequest, responseObserver: StreamObserver<SearchPeopleResponse>) {
        handle(responseObserver) {
            val result = app.searchPeople(
                SearchPeopleCommand(request.query, request.limit, request.offset),
            )
            SearchPeopleResponse.newBuilder()
                .addAllPeople(result.people.map { it.toProto() })
                .setTotal(result.total)
                .build()
        }
    }

    override fun createPerson(request: CreatePersonRequest, responseObserver: StreamObserver<PersonResponse>) {
        handle(responseObserver) {
            val command = CreatePersonCommand(
                tmdbId = if (request.hasTmdbId()) request.tmdbId else null,
                name = request.name,
                biography = request.biography,
                birthDate = DateHelpers.parseOptional(if (request.hasBirthDate()) request.birthDate else null, "birthDate"),
                deathDate = DateHelpers.parseOptional(if (request.hasDeathDate()) request.deathDate else null, "deathDate"),
                placeOfBirth = if (request.hasPlaceOfBirth()) request.placeOfBirth else null,
                profilePath = if (request.hasProfilePath()) request.profilePath else null,
            )
            app.createPerson(command).toProto()
        }
    }

    override fun updatePerson(request: UpdatePersonRequest, responseObserver: StreamObserver<PersonResponse>) {
        handle(responseObserver) {
            if (!request.hasPatch()) throw ValidationException("patch is required")
            if (!request.hasUpdateMask()) throw ValidationException("update_mask is required")
            val patch = request.patch
            val maskPaths = request.updateMask.pathsList.toSet()
            val command = UpdatePersonCommand(
                id = parseId(request.id),
                expectedVersion = request.expectedVersion,
                maskPaths = maskPaths,
                name = patch.name,
                biography = patch.biography,
                birthDate = DateHelpers.parseOptional(if (patch.hasBirthDate()) patch.birthDate else null, "birthDate"),
                deathDate = DateHelpers.parseOptional(if (patch.hasDeathDate()) patch.deathDate else null, "deathDate"),
                placeOfBirth = if (patch.hasPlaceOfBirth()) patch.placeOfBirth else null,
                profilePath = if (patch.hasProfilePath()) patch.profilePath else null,
            )
            app.updatePerson(command).toProto()
        }
    }

    override fun deletePerson(request: DeletePersonRequest, responseObserver: StreamObserver<Empty>) {
        handle(responseObserver) {
            app.deletePerson(parseId(request.id))
            Empty.getDefaultInstance()
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun parseId(raw: String): UUID {
        val s = raw.trim()
        if (s.isEmpty()) throw ValidationException("id is required")
        return try {
            UUID.fromString(s)
        } catch (e: IllegalArgumentException) {
            throw ValidationException("id '$raw' is not a valid UUID")
        }
    }

    /**
     * Runs [block], sends its result, and maps any exception to a gRPC Status.
     * Centralizes the deliberate exception->status contract.
     */
    private fun <T> handle(observer: StreamObserver<T>, block: () -> T) {
        try {
            val result = block()
            observer.onNext(result)
            observer.onCompleted()
        } catch (e: Exception) {
            val trailers = Metadata()
            if (e is ValidationException) {
                e.field?.let { trailers.put(FIELD_METADATA_KEY, it) }
            }
            observer.onError(e.toStatus().asRuntimeException(trailers))
        }
    }

    private fun Throwable.toStatus(): Status = when (this) {
        is ValidationException -> Status.INVALID_ARGUMENT.withDescription(message)
        is PersonNotFoundException -> Status.NOT_FOUND.withDescription(message)
        is DuplicateTmdbIdException -> Status.ALREADY_EXISTS.withDescription(message)
        is VersionConflictException -> Status.ABORTED.withDescription(message)
        is PreconditionException -> Status.FAILED_PRECONDITION.withDescription(message)
        // Persistence/connectivity problems surface as UNAVAILABLE (§13 resilience).
        is DataAccessResourceFailureException,
        is CannotCreateTransactionException,
        -> Status.UNAVAILABLE.withDescription("people datastore unavailable")
        is StatusRuntimeException -> this.status
        else -> Status.INTERNAL.withDescription(this.message)
    }

    companion object {
        /** Carries ValidationException.field across the gRPC boundary (mirrors x-correlation-id). */
        val FIELD_METADATA_KEY: Metadata.Key<String> =
            Metadata.Key.of("x-field", Metadata.ASCII_STRING_MARSHALLER)
    }
}

private fun PersonView.toProto(): PersonResponse {
    val b = PersonResponse.newBuilder()
        .setId(id.toString())
        .setName(name)
        .setBiography(biography)
        .setVersion(version)
    tmdbId?.let { b.tmdbId = it }
    DateHelpers.format(birthDate)?.let { b.birthDate = it }
    DateHelpers.format(deathDate)?.let { b.deathDate = it }
    placeOfBirth?.let { b.placeOfBirth = it }
    profilePath?.let { b.profilePath = it }
    return b.build()
}
