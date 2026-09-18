package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.application.CommentView
import com.moviecatalogue.catalogue.application.CreditView
import com.moviecatalogue.catalogue.application.MovieView
import com.moviecatalogue.catalogue.application.PersonCreditView
import com.moviecatalogue.catalogue.artwork.ArtworkAsset
import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.people.CountryCodeData
import com.moviecatalogue.catalogue.people.PersonData
import com.moviecatalogue.catalogue.reference.CreditRoleCode
import com.moviecatalogue.catalogue.reference.GenreCode
import com.moviecatalogue.catalogue.reference.LanguageCode

fun Movie.toGql() = MovieGql(
    id = id.toString(),
    title = title,
    originalTitle = originalTitle,
    synopsis = synopsis,
    releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes,
    originalLanguage = originalLanguage,
    version = version,
)

fun MovieView.toGql() = MovieGql(
    id = id.toString(),
    title = title,
    originalTitle = originalTitle,
    synopsis = synopsis,
    releaseDate = releaseDate,
    runtimeMinutes = runtimeMinutes,
    originalLanguage = originalLanguage,
    version = version,
)

fun MovieCredit.toGql() = MovieCreditGql(
    id = id.toString(),
    category = category,
    roleCode = roleCode,
    characterName = characterName,
    sourceRoleName = sourceRoleName,
    billingOrder = billingOrder,
    personId = personId.toString(),
)

/** CreditView already carries a resolved person (used by single-credit mutation returns). */
fun CreditView.toGql() = MovieCreditGql(
    id = id.toString(),
    category = category,
    roleCode = roleCode,
    characterName = characterName,
    sourceRoleName = sourceRoleName,
    billingOrder = billingOrder,
    personId = person.id.toString(),
)

/**
 * The same-origin proxy path (never a raw storage URL) that the frontend already
 * serves person photos from (`/api/people/{id}/photo`, see interfaces.md); null when
 * the person has no uploaded photo. Mirrors how ArtworkAsset.toGql's `url` embeds the
 * frontend's own proxy path for movie artwork.
 */
fun PersonData.toGql() = PersonGql(
    id = id.toString(),
    name = name,
    biography = biography,
    birthDate = birthDate,
    deathDate = deathDate,
    placeOfBirth = placeOfBirth,
    birthCountryCode = birthCountryCode,
    version = version,
    photoUrl = if (profilePath != null) "/api/people/$id/photo" else null,
)

fun PersonCreditView.toGql() = PersonCreditGql(
    movieId = movieId.toString(),
    movieTitle = movieTitle,
    category = category,
    roleCode = roleCode,
    characterName = characterName,
)

fun GenreCode.toGql() = GenreCodeGql(code = code, title = title, description = description, active = active)

fun LanguageCode.toGql() = LanguageCodeGql(code = code, name = name, active = active)

fun CountryCodeData.toGql() = CountryCodeGql(code = code, name = name, active = active)

fun CreditRoleCode.toGql() = CreditRoleCodeGql(
    code = code, title = title, category = category, department = department, description = description, active = active,
)

fun ArtworkAsset.toGql(url: String) = ArtworkGql(
    id = id.toString(), url = url, mediaType = mediaType, byteSize = byteSize,
)

fun CommentView.toGql() = MovieCommentGql(
    id = id.toString(), authorDisplayName = authorDisplayName, text = text, createdAt = createdAt,
)
