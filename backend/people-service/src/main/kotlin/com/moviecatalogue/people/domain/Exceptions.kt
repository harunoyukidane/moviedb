package com.moviecatalogue.people.domain

/**
 * Domain-level exceptions. The gRPC inbound adapter maps each of these to a
 * deliberate gRPC Status (see spec §8.3 / phase-2 task 4). Keeping them here,
 * free of any gRPC or Spring types, preserves the pure-domain boundary and lets
 * the use cases be unit-tested without a container.
 */
sealed class PeopleException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Invalid input (blank/overlong name, bad date, bad ID, bad query). -> INVALID_ARGUMENT.
 * [field] is the GraphQL field name (not the DB column), carried to the Catalogue
 * over gRPC metadata so the BFF can key the message straight to a form input.
 */
class ValidationException(message: String, val field: String? = null) : PeopleException(message)

/** A requested person does not exist. -> NOT_FOUND */
class PersonNotFoundException(message: String = "person not found") : PeopleException(message)

/** A person with the same tmdb_id already exists. -> ALREADY_EXISTS */
class DuplicateTmdbIdException(tmdbId: Long) :
    PeopleException("a person with tmdb_id=$tmdbId already exists")

/** Optimistic-lock conflict: expected_version did not match. -> ABORTED */
class VersionConflictException(message: String = "person was modified concurrently") :
    PeopleException(message)

/** Malformed request that is structurally wrong (e.g. bad field mask path). -> FAILED_PRECONDITION */
class PreconditionException(message: String) : PeopleException(message)
