package com.moviecatalogue.people.application

import java.time.LocalDate
import java.util.UUID

/**
 * Application-layer commands/results. These are plain Kotlin types, independent of
 * proto and Spring, so use cases can be unit-tested directly. The gRPC adapter maps
 * proto messages <-> these types.
 */

data class CreatePersonCommand(
    val tmdbId: Long?,
    val name: String,
    val biography: String,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
    val profilePath: String?,
)

/**
 * A patch for update. Each field is paired with a flag indicating whether the
 * field mask selected it, so callers can distinguish "set to null/empty" from
 * "not in mask". [maskPaths] is the raw set of requested field-mask paths.
 */
data class UpdatePersonCommand(
    val id: UUID,
    val expectedVersion: Long,
    val maskPaths: Set<String>,
    val name: String?,
    val biography: String?,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
    val profilePath: String?,
)

data class SearchPeopleCommand(
    val query: String,
    val limit: Int,
    val offset: Int,
)

data class SearchPeopleResult(
    val people: List<PersonView>,
    val total: Long,
)

/** Read-model view of a person returned by use cases. */
data class PersonView(
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
