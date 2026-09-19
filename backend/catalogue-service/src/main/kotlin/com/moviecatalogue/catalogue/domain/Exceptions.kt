package com.moviecatalogue.catalogue.domain

/**
 * Domain exceptions for the Catalogue service. The GraphQL error boundary
 * (phase-3 task 5) maps each to a stable `extensions.code` (§8.2). Kept free of
 * Spring/GraphQL/gRPC types so the domain and use cases stay unit-testable.
 */
sealed class CatalogueException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Field/rule validation failed. -> BAD_USER_INPUT. [field] is the GraphQL field
 * name (not the DB column) so the BFF can key the message straight to a form
 * input; null when the violation isn't attributable to one field.
 */
class ValidationException(message: String, val field: String? = null) : CatalogueException(message)

/** Requested movie/person/credit/code does not exist. -> NOT_FOUND */
class NotFoundException(message: String) : CatalogueException(message)

/** Duplicate record or stale optimistic-lock version. -> CONFLICT */
class ConflictException(message: String) : CatalogueException(message)

/** Delete rejected because credits still reference the person. -> PERSON_IN_USE */
class PersonInUseException(message: String = "person is referenced by one or more credits") :
    CatalogueException(message)

/**
 * A person's birth/death date would contradict a movie they're already
 * credited on (V2.8-03). -> PERSON_DATE_CONFLICTS_CREDIT. [field] is
 * "birthDate" or "deathDate" so the BFF can surface it against that input.
 */
class PersonDateConflictsCreditException(message: String, val field: String) : CatalogueException(message)

/** People Service failed or timed out. -> DEPENDENCY_UNAVAILABLE */
class DependencyUnavailableException(message: String = "people service unavailable", cause: Throwable? = null) :
    CatalogueException(message, cause)
