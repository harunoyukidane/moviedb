package com.moviecatalogue.catalogue.graphql

import java.time.LocalDate

/**
 * GraphQL input DTOs. Spring for GraphQL binds argument maps onto these via the
 * Jackson/`@Argument` mechanism. Nullable fields distinguish "omitted" for
 * update masks; the resolver decides mask membership from the input map.
 */

data class PageInput(val limit: Int = 20, val offset: Int = 0)

data class MovieFilterInput(
    val genreCode: String? = null,
    val releaseYear: Int? = null,
)

data class CreateMovieInput(
    val title: String,
    val originalTitle: String? = null,
    val synopsis: String = "",
    val releaseDate: LocalDate? = null,
    val runtimeMinutes: Int? = null,
    val originalLanguage: String? = null,
    val genreCodes: List<String> = emptyList(),
    val tmdbId: Long? = null,
)

data class UpdateMovieInput(
    val title: String? = null,
    val originalTitle: String? = null,
    val synopsis: String? = null,
    val releaseDate: LocalDate? = null,
    val runtimeMinutes: Int? = null,
    val originalLanguage: String? = null,
    val genreCodes: List<String>? = null,
)

data class CreatePersonInput(
    val name: String,
    val biography: String = "",
    val birthDate: LocalDate? = null,
    val deathDate: LocalDate? = null,
    val placeOfBirth: String? = null,
)

data class UpdatePersonInput(
    val name: String? = null,
    val biography: String? = null,
    val birthDate: LocalDate? = null,
    val deathDate: LocalDate? = null,
    val placeOfBirth: String? = null,
)

data class CreateCreditInput(
    val personId: String,
    val roleCode: String,
    val characterName: String? = null,
    val billingOrder: Int? = null,
    val tmdbCreditId: String? = null,
    val sourceRoleName: String? = null,
)

data class UpdateCreditInput(
    val roleCode: String? = null,
    val characterName: String? = null,
    val billingOrder: Int? = null,
)

data class AddMovieCommentInput(
    val authorDisplayName: String,
    val text: String,
)
