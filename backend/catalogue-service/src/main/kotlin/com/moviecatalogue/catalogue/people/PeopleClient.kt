package com.moviecatalogue.catalogue.people

import java.time.LocalDate
import java.util.UUID

/**
 * Outbound port to the People Service (§6.4). The application layer depends on
 * this interface, not on gRPC types, so use cases can be unit-tested with a fake.
 * The gRPC adapter (PeopleGrpcClient) applies deadlines and maps gRPC Status to
 * catalogue domain exceptions.
 */
interface PeopleClient {
    /** Fetch a single person; throws NotFoundException if absent, DependencyUnavailable on failure. */
    fun getPerson(id: UUID): PersonData

    /** Batch fetch, returning only the people that exist, keyed by ID. Never N+1. */
    fun getPeople(ids: Collection<UUID>): Map<UUID, PersonData>

    /** Name search over the People Service (§9), returning lightweight hits. */
    fun searchPeople(query: String, limit: Int, offset: Int): List<PersonHit>

    /** Paged people listing/search returning full people + total (blank query = list all). */
    fun searchPeoplePage(query: String?, limit: Int, offset: Int): PersonPage

    fun createPerson(command: CreatePersonData): PersonData

    fun updatePerson(command: UpdatePersonData): PersonData

    /** Physical delete on the People side (safe-delete guard is enforced by the caller). */
    fun deletePerson(id: UUID)
}

data class PersonData(
    val id: UUID,
    val tmdbId: Long?,
    val name: String,
    val biography: String,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
    val profilePath: String?,
    val version: Long,
)

/** Lightweight person search hit (§9 SearchPeople). */
data class PersonHit(
    val id: UUID,
    val name: String,
)

/** A page of full people plus the total match count. */
data class PersonPage(
    val items: List<PersonData>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

data class CreatePersonData(
    val name: String,
    val biography: String,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
)

data class UpdatePersonData(
    val id: UUID,
    val expectedVersion: Long,
    val maskPaths: Set<String>,
    val name: String?,
    val biography: String?,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
)
