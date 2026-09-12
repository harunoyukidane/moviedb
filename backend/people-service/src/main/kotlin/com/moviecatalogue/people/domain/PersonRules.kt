package com.moviecatalogue.people.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Pure-domain invariants for a person and the request-shaping limits from §8.3.
 * No Spring, no JPA, no gRPC: unit-testable in isolation.
 */
object PersonRules {
    const val NAME_MAX = 300
    const val PLACE_OF_BIRTH_MAX = 300
    const val PROFILE_PATH_MAX = 500

    // Batch and search limits (§8.3).
    const val MAX_BATCH_IDS = 200
    const val SEARCH_LIMIT_MIN = 1
    const val SEARCH_LIMIT_MAX = 100
    const val SEARCH_LIMIT_DEFAULT = 20
    const val QUERY_MIN_LEN = 1
    const val QUERY_MAX_LEN = 100

    /** Trim and validate a person name. Returns the normalized (trimmed) value. */
    fun normalizeName(raw: String?): String {
        val name = raw?.trim().orEmpty()
        if (name.isEmpty()) throw ValidationException("name must not be blank")
        if (name.length > NAME_MAX) {
            throw ValidationException("name must be at most $NAME_MAX characters")
        }
        return name
    }

    /** Optional free-text field with an upper bound; blank becomes null. */
    fun normalizeOptionalText(raw: String?, max: Int, field: String): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > max) throw ValidationException("$field must be at most $max characters")
        return v
    }

    /** Enforce death_date >= birth_date when both are present. */
    fun validateLifeDates(birthDate: LocalDate?, deathDate: LocalDate?) {
        if (birthDate != null && deathDate != null && deathDate.isBefore(birthDate)) {
            throw ValidationException("death_date must not be before birth_date")
        }
    }

    /** Clamp a requested search limit into [1, 100], defaulting when non-positive. */
    fun clampLimit(requested: Int): Int = when {
        requested <= 0 -> SEARCH_LIMIT_DEFAULT
        requested < SEARCH_LIMIT_MIN -> SEARCH_LIMIT_MIN
        requested > SEARCH_LIMIT_MAX -> SEARCH_LIMIT_MAX
        else -> requested
    }

    /** Clamp a requested offset to be non-negative. */
    fun clampOffset(requested: Int): Int = if (requested < 0) 0 else requested

    /** Validate and normalize a search query string. */
    fun normalizeQuery(raw: String?): String {
        val q = raw?.trim().orEmpty()
        if (q.length < QUERY_MIN_LEN) {
            throw ValidationException("query must be at least $QUERY_MIN_LEN character")
        }
        if (q.length > QUERY_MAX_LEN) {
            throw ValidationException("query must be at most $QUERY_MAX_LEN characters")
        }
        return q
    }

    /**
     * Deduplicate requested IDs (preserving first-seen order) and enforce the batch cap.
     * The cap is checked on the de-duplicated set, matching "distinct people requested".
     */
    fun <T> dedupeAndCap(ids: List<T>): List<T> {
        val distinct = ids.distinct()
        if (distinct.size > MAX_BATCH_IDS) {
            throw ValidationException("at most $MAX_BATCH_IDS ids may be requested, got ${distinct.size}")
        }
        return distinct
    }
}

/**
 * Proto carries ISO dates as `optional string`. These helpers parse/format at the
 * boundary; parsing throws ValidationException on malformed input (-> INVALID_ARGUMENT).
 */
object DateHelpers {
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Parse a blank-or-null string to null, an ISO `yyyy-MM-dd` string to a date. */
    fun parseOptional(raw: String?): LocalDate? {
        val s = raw?.trim()
        if (s.isNullOrEmpty()) return null
        return try {
            LocalDate.parse(s, ISO)
        } catch (e: DateTimeParseException) {
            throw ValidationException("invalid date '$s'; expected ISO yyyy-MM-dd")
        }
    }

    fun format(date: LocalDate?): String? = date?.format(ISO)
}
