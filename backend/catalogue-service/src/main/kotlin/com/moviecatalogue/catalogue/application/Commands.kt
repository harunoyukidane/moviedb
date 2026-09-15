package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.domain.CreditCategory
import java.time.LocalDate
import java.util.UUID

/** Application commands/results, independent of GraphQL and JPA types. */

data class CreateMovieCommand(
    val title: String,
    val originalTitle: String?,
    val synopsis: String,
    val releaseDate: LocalDate?,
    val runtimeMinutes: Int?,
    val originalLanguage: String?,
    val genreCodes: List<String>,
    /** Optional TMDB provenance id; when set, create is idempotent (upsert). */
    val tmdbId: Long? = null,
)

data class UpdateMovieCommand(
    val id: UUID,
    val expectedVersion: Long,
    val maskTitle: Boolean, val title: String?,
    val maskOriginalTitle: Boolean, val originalTitle: String?,
    val maskSynopsis: Boolean, val synopsis: String?,
    val maskReleaseDate: Boolean, val releaseDate: LocalDate?,
    val maskRuntime: Boolean, val runtimeMinutes: Int?,
    val maskOriginalLanguage: Boolean, val originalLanguage: String?,
    val maskGenres: Boolean, val genreCodes: List<String>?,
)

data class AddCreditCommand(
    val movieId: UUID,
    val personId: UUID,
    val roleCode: String,
    val characterName: String?,
    val billingOrder: Int?,
    /** Optional TMDB credit id; when set, add is idempotent (upsert). */
    val tmdbCreditId: String? = null,
    /** Optional original TMDB job/role, preserved as provenance. */
    val sourceRoleName: String? = null,
)

data class UpdateCreditCommand(
    val id: UUID,
    val maskRole: Boolean, val roleCode: String?,
    val maskCharacter: Boolean, val characterName: String?,
    val maskBilling: Boolean, val billingOrder: Int?,
)

// --- views -------------------------------------------------------------------

data class MovieView(
    val id: UUID,
    val title: String,
    val originalTitle: String?,
    val synopsis: String,
    val releaseDate: LocalDate?,
    val runtimeMinutes: Int?,
    val originalLanguage: String?,
    val version: Long,
)

data class MoviePageView(
    val items: List<MovieView>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

/** Optional AND-combined movie-listing filter (§8.1). */
data class MovieFilter(
    val genreCode: String? = null,
    val releaseYear: Int? = null,
)

/** A credit plus the resolved (possibly unavailable) person reference. */
data class CreditView(
    val id: UUID,
    val movieId: UUID,
    val category: CreditCategory,
    val roleCode: String,
    val characterName: String?,
    val sourceRoleName: String?,
    val billingOrder: Int?,
    val person: PersonRef,
)

/** Degraded-tolerant person reference (§6.5/§13). */
data class PersonRef(
    val id: UUID,
    val name: String,
    val available: Boolean,
)
