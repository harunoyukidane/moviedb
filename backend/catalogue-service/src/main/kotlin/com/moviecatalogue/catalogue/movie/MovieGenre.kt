package com.moviecatalogue.catalogue.movie

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/** Composite PK for the movie<->genre association (§7.2). */
@Embeddable
class MovieGenreId(
    @Column(name = "movie_id", nullable = false)
    var movieId: UUID,

    @Column(name = "genre_code", nullable = false)
    var genreCode: String,
) : Serializable {
    override fun equals(other: Any?): Boolean =
        other is MovieGenreId && other.movieId == movieId && other.genreCode == genreCode

    override fun hashCode(): Int = 31 * movieId.hashCode() + genreCode.hashCode()
}

/** Join row assigning a genre code to a movie. Deleted by ON DELETE CASCADE with the movie. */
@Entity
@Table(name = "movie_genre")
class MovieGenre(
    @EmbeddedId
    var id: MovieGenreId,
)
