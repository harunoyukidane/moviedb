package com.moviecatalogue.catalogue.domain

import java.time.Clock
import java.time.LocalDate

/** Credit category, mirrors the DB `credit_category` enum and the GraphQL enum. */
enum class CreditCategory { CAST, CREW }

/**
 * Pure-domain invariants for movies and credits (§6.1/§6.6, §7.2). No Spring/JPA.
 * These enforce the same rules the DB constraints do, so violations surface as
 * BAD_USER_INPUT before hitting the database.
 */
object MovieRules {
    const val TITLE_MAX = 300
    const val ORIGINAL_LANGUAGE_MAX = 10

    // Pagination (§8.1 "limit clamped to 1-100").
    const val PAGE_LIMIT_MIN = 1
    const val PAGE_LIMIT_MAX = 100
    const val PAGE_LIMIT_DEFAULT = 20

    // Bounded four-digit release year for movie filtering.
    const val RELEASE_YEAR_MIN = 1888
    const val RELEASE_YEAR_MAX = 2100

    // Release-date semantics (V2.2-03): the first film ever made, and the longest
    // real announcement lead time (Avatar-sequel-scale) with room to spare.
    private val RELEASE_DATE_FLOOR: LocalDate = LocalDate.of(1888, 10, 14)
    private const val RELEASE_DATE_HORIZON_YEARS = 10L

    fun normalizeTitle(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) throw ValidationException("title must not be blank", field = "title")
        if (t.length > TITLE_MAX) throw ValidationException("title must be at most $TITLE_MAX characters", field = "title")
        return t
    }

    fun normalizeOptionalText(raw: String?, max: Int, field: String): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > max) throw ValidationException("$field must be at most $max characters", field = field)
        return v
    }

    fun validateRuntime(runtimeMinutes: Int?) {
        if (runtimeMinutes != null && runtimeMinutes <= 0) {
            throw ValidationException("runtimeMinutes must be positive", field = "runtimeMinutes")
        }
    }

    fun clampLimit(requested: Int): Int = when {
        requested <= 0 -> PAGE_LIMIT_DEFAULT
        requested < PAGE_LIMIT_MIN -> PAGE_LIMIT_MIN
        requested > PAGE_LIMIT_MAX -> PAGE_LIMIT_MAX
        else -> requested
    }

    fun clampOffset(requested: Int): Int = if (requested < 0) 0 else requested

    fun validateReleaseYear(year: Int?) {
        if (year != null && (year < RELEASE_YEAR_MIN || year > RELEASE_YEAR_MAX)) {
            throw ValidationException(
                "releaseYear must be between $RELEASE_YEAR_MIN and $RELEASE_YEAR_MAX",
                field = "releaseYear",
            )
        }
    }

    /**
     * A movie's release date must not precede cinema itself, and must not be
     * further out than the longest real announcement lead time (10 years).
     * Deliberately future-permissive otherwise: a "coming soon" entry is legitimate.
     */
    fun validateReleaseDate(date: LocalDate?, clock: Clock) {
        if (date == null) return
        if (date.isBefore(RELEASE_DATE_FLOOR)) {
            throw ValidationException(
                "Release date $date is before the first film was made (14 October 1888).",
                field = "releaseDate",
            )
        }
        val ceiling = LocalDate.now(clock).plusYears(RELEASE_DATE_HORIZON_YEARS)
        if (date.isAfter(ceiling)) {
            throw ValidationException(
                "Release date $date is more than $RELEASE_DATE_HORIZON_YEARS years away — " +
                    "further ahead than films are ever announced. Check the year.",
                field = "releaseDate",
            )
        }
    }
}

object CreditRules {
    const val CHARACTER_NAME_MAX = 300
    const val SOURCE_ROLE_NAME_MAX = 150

    /**
     * Enforce the cast/crew character rule and role/category agreement (§7.2):
     * - CAST requires a non-blank character name.
     * - CREW must not carry a character name.
     * - The declared role's category must match the credit category.
     */
    fun validateCharacterForCategory(category: CreditCategory, characterName: String?) {
        val hasCharacter = !characterName.isNullOrBlank()
        when (category) {
            CreditCategory.CAST ->
                if (!hasCharacter) throw ValidationException("cast credits require a characterName", field = "characterName")
            CreditCategory.CREW ->
                if (hasCharacter) throw ValidationException("crew credits must not have a characterName", field = "characterName")
        }
    }

    fun validateRoleCategoryAgreement(roleCategory: CreditCategory, creditCategory: CreditCategory) {
        if (roleCategory != creditCategory) {
            throw ValidationException(
                "role category $roleCategory does not match credit category $creditCategory",
                field = "roleCode",
            )
        }
    }

    fun validateBillingOrder(billingOrder: Int?) {
        if (billingOrder != null && billingOrder < 0) {
            throw ValidationException("billingOrder must be zero or positive", field = "billingOrder")
        }
    }

    /** A role/genre/language code must exist and be active to be assignable. */
    fun requireActiveCode(exists: Boolean, active: Boolean, kind: String, code: String, field: String? = null) {
        if (!exists) throw ValidationException("$kind code '$code' does not exist", field = field)
        if (!active) throw ValidationException("$kind code '$code' is inactive", field = field)
    }

    /** A code must exist in controlled reference data, e.g. for filtering (active/inactive both allowed). */
    fun requireExistingCode(exists: Boolean, kind: String, code: String, field: String? = null) {
        if (!exists) throw ValidationException("$kind code '$code' does not exist", field = field)
    }

    fun normalizeCharacterName(raw: String?): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > CHARACTER_NAME_MAX) {
            throw ValidationException("characterName must be at most $CHARACTER_NAME_MAX characters", field = "characterName")
        }
        return v
    }

    fun normalizeSourceRoleName(raw: String?): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > SOURCE_ROLE_NAME_MAX) {
            throw ValidationException("sourceRoleName must be at most $SOURCE_ROLE_NAME_MAX characters", field = "sourceRoleName")
        }
        return v
    }

    /**
     * A person cannot be credited on a movie released before they were born
     * (V2.2-03b). Deliberately not checking a minimum age at release - an actor
     * can genuinely have been credited as an infant on an old film.
     */
    fun isBornAfterRelease(personBirthDate: LocalDate?, movieReleaseDate: LocalDate?): Boolean =
        personBirthDate != null && movieReleaseDate != null && personBirthDate.isAfter(movieReleaseDate)

    fun validateCreditAge(personName: String, personBirthDate: LocalDate?, movieReleaseDate: LocalDate?, field: String? = null) {
        if (isBornAfterRelease(personBirthDate, movieReleaseDate)) {
            throw ValidationException(
                "Can't save: $personName was born on $personBirthDate, after this movie's release on " +
                    "$movieReleaseDate. Check the dates and try again.",
                field = field,
            )
        }
    }
}

/** Pure-domain invariants for movie comments (V2-13). No Spring/JPA. */
object MovieCommentRules {
    const val AUTHOR_DISPLAY_NAME_MAX = 50
    const val TEXT_MAX = 2000

    fun normalizeAuthorDisplayName(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) throw ValidationException("authorDisplayName must not be blank", field = "authorDisplayName")
        if (v.length > AUTHOR_DISPLAY_NAME_MAX) {
            throw ValidationException(
                "authorDisplayName must be at most $AUTHOR_DISPLAY_NAME_MAX characters",
                field = "authorDisplayName",
            )
        }
        return v
    }

    fun normalizeText(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) throw ValidationException("text must not be blank", field = "text")
        if (v.length > TEXT_MAX) throw ValidationException("text must be at most $TEXT_MAX characters", field = "text")
        return v
    }
}
