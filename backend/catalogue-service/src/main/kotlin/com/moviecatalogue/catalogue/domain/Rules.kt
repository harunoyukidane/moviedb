package com.moviecatalogue.catalogue.domain

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

    fun normalizeTitle(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) throw ValidationException("title must not be blank")
        if (t.length > TITLE_MAX) throw ValidationException("title must be at most $TITLE_MAX characters")
        return t
    }

    fun normalizeOptionalText(raw: String?, max: Int, field: String): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > max) throw ValidationException("$field must be at most $max characters")
        return v
    }

    fun validateRuntime(runtimeMinutes: Int?) {
        if (runtimeMinutes != null && runtimeMinutes <= 0) {
            throw ValidationException("runtimeMinutes must be positive")
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
            throw ValidationException("releaseYear must be between $RELEASE_YEAR_MIN and $RELEASE_YEAR_MAX")
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
                if (!hasCharacter) throw ValidationException("cast credits require a characterName")
            CreditCategory.CREW ->
                if (hasCharacter) throw ValidationException("crew credits must not have a characterName")
        }
    }

    fun validateRoleCategoryAgreement(roleCategory: CreditCategory, creditCategory: CreditCategory) {
        if (roleCategory != creditCategory) {
            throw ValidationException(
                "role category $roleCategory does not match credit category $creditCategory",
            )
        }
    }

    fun validateBillingOrder(billingOrder: Int?) {
        if (billingOrder != null && billingOrder < 0) {
            throw ValidationException("billingOrder must be zero or positive")
        }
    }

    /** A role/genre code must exist and be active to be assignable. */
    fun requireActiveCode(exists: Boolean, active: Boolean, kind: String, code: String) {
        if (!exists) throw ValidationException("$kind code '$code' does not exist")
        if (!active) throw ValidationException("$kind code '$code' is inactive")
    }

    /** A code must exist in controlled reference data, e.g. for filtering (active/inactive both allowed). */
    fun requireExistingCode(exists: Boolean, kind: String, code: String) {
        if (!exists) throw ValidationException("$kind code '$code' does not exist")
    }

    fun normalizeCharacterName(raw: String?): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > CHARACTER_NAME_MAX) {
            throw ValidationException("characterName must be at most $CHARACTER_NAME_MAX characters")
        }
        return v
    }

    fun normalizeSourceRoleName(raw: String?): String? {
        val v = raw?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.length > SOURCE_ROLE_NAME_MAX) {
            throw ValidationException("sourceRoleName must be at most $SOURCE_ROLE_NAME_MAX characters")
        }
        return v
    }
}
