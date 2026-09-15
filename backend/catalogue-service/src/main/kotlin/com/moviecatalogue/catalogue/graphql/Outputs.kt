package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.domain.CreditCategory
import java.time.LocalDate

/**
 * GraphQL output DTOs matching the SDL. Nested fields (Movie.cast/creators/
 * genres/artwork, MovieCredit.role/person, Person.credits) are resolved lazily
 * by @SchemaMapping methods so list queries can batch person hydration.
 */

data class MovieGql(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val synopsis: String,
    val releaseDate: LocalDate?,
    val runtimeMinutes: Int?,
    val originalLanguage: String?,
    val version: Long,
)

data class MoviePageGql(
    val items: List<MovieGql>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

data class MovieCreditGql(
    val id: String,
    val category: CreditCategory,
    val roleCode: String,
    val characterName: String?,
    val sourceRoleName: String?,
    val billingOrder: Int?,
    // person id carried for the field resolver / DataLoader
    val personId: String,
)

data class PersonReferenceGql(val id: String, val name: String, val available: Boolean)

data class PersonGql(
    val id: String,
    val name: String,
    val biography: String,
    val birthDate: LocalDate?,
    val deathDate: LocalDate?,
    val placeOfBirth: String?,
    val version: Long,
    val photoUrl: String?,
)

data class PersonCreditGql(
    val movieId: String,
    val movieTitle: String,
    val category: CreditCategory,
    val roleCode: String,
    val characterName: String?,
)

data class PersonPageGql(
    val items: List<PersonGql>,
    val total: Long,
    val limit: Int,
    val offset: Int,
)

data class ArtworkGql(val id: String, val url: String, val mediaType: String, val byteSize: Long)

data class GenreCodeGql(val code: String, val title: String, val description: String, val active: Boolean)

data class CreditRoleCodeGql(
    val code: String,
    val title: String,
    val category: CreditCategory,
    val department: String?,
    val description: String,
    val active: Boolean,
)

data class DeleteResultGql(val deletedId: String)

data class MovieSearchHitGql(
    val id: String,
    val title: String,
    val releaseDate: LocalDate?,
    val matchedPersonNames: List<String>,
)

data class PersonSearchHitGql(val id: String, val name: String)

data class SearchResultGql(
    val movies: List<MovieSearchHitGql>,
    val people: List<PersonSearchHitGql>,
)
