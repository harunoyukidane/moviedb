package com.moviecatalogue.catalogue.movie

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MovieRepository : JpaRepository<Movie, UUID> {
    /** Paged listing ordered by the caller-supplied Pageable (title asc by default). */
    override fun findAll(pageable: Pageable): Page<Movie>

    /** Lookup by TMDB provenance id for idempotent import upserts (§12.3). */
    fun findByTmdbId(tmdbId: Long): Movie?

    /**
     * Case-insensitive title / original-title search (§9). Matches on
     * `lower(title)` (uses ix_movie_title_lower) and `lower(original_title)`.
     * The pattern's user-supplied `%`/`_`/`\` are escaped by the caller and
     * `ESCAPE '\'` makes them literal (§14).
     */
    @Query(
        """
        SELECT m FROM Movie m
        WHERE lower(m.title) LIKE lower(:pattern) ESCAPE '\'
           OR lower(m.originalTitle) LIKE lower(:pattern) ESCAPE '\'
        ORDER BY lower(m.title) ASC, m.id ASC
        """,
    )
    fun searchByTitlePattern(@Param("pattern") pattern: String, pageable: Pageable): List<Movie>

    /**
     * Paged listing filtered by an optional genre code and/or release year,
     * combined with AND semantics (§8.1). The genre match is an EXISTS
     * subquery against `movie_genre` rather than a join, so a movie with
     * multiple matching rows is never duplicated in the result.
     */
    @Query(
        value = """
        SELECT m FROM Movie m
        WHERE (:genreCode IS NULL OR EXISTS (
            SELECT 1 FROM MovieGenre mg
            WHERE mg.id.movieId = m.id AND mg.id.genreCode = :genreCode
        ))
        AND (:releaseYear IS NULL OR YEAR(m.releaseDate) = :releaseYear)
        """,
        countQuery = """
        SELECT COUNT(m) FROM Movie m
        WHERE (:genreCode IS NULL OR EXISTS (
            SELECT 1 FROM MovieGenre mg
            WHERE mg.id.movieId = m.id AND mg.id.genreCode = :genreCode
        ))
        AND (:releaseYear IS NULL OR YEAR(m.releaseDate) = :releaseYear)
        """,
    )
    fun findAllByFilter(
        @Param("genreCode") genreCode: String?,
        @Param("releaseYear") releaseYear: Int?,
        pageable: Pageable,
    ): Page<Movie>
}

interface MovieGenreRepository : JpaRepository<MovieGenre, MovieGenreId> {
    fun findAllByIdMovieId(movieId: UUID): List<MovieGenre>
    fun findAllByIdMovieIdIn(movieIds: Collection<UUID>): List<MovieGenre>
    fun deleteByIdMovieId(movieId: UUID)
}
