package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.domain.NotFoundException
import java.util.UUID

/**
 * Parse a GraphQL string argument as an id. A malformed UUID is `NOT_FOUND`
 * rather than `INTERNAL_ERROR` (V2.2-07): from the caller's perspective,
 * `"abc"` is exactly as absent as a well-formed id that simply doesn't exist,
 * and `/movies/abc` should render the app's 404 rather than a 503.
 *
 * Mirrors the pattern `PeopleGrpcService.parseId` already uses at the gRPC
 * boundary: catch `IllegalArgumentException` from `UUID.fromString` and
 * translate it to a domain exception instead of letting it propagate raw.
 *
 * @param kind the entity name used in the not-found message ("movie", "person", "credit").
 */
fun parseId(raw: String, kind: String): UUID =
    try {
        UUID.fromString(raw)
    } catch (e: IllegalArgumentException) {
        throw NotFoundException("$kind '$raw' not found")
    }
