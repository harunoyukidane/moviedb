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
     *
     * Ordered by `lower(title) COLLATE "und-x-icu"` (matches [countTitlesBefore])
     * so an accented title interleaves with its base letter instead of trailing
     * after every ASCII title (this DB's default libc collation compares by raw
     * code point) - the caller passes an unsorted Pageable so this ORDER BY isn't
     * overridden. Native SQL because JPQL/HQL's `collate()` function only accepts
     * a bare-identifier collation name, and "und-x-icu" isn't one (see V8
     * migration for the matching index).
     */
    @Query(
        value = """
        SELECT * FROM movie m
        WHERE (:genreCode IS NULL OR EXISTS (
            SELECT 1 FROM movie_genre mg
            WHERE mg.movie_id = m.id AND mg.genre_code = :genreCode
        ))
        AND (:releaseYear IS NULL OR EXTRACT(YEAR FROM m.release_date) = :releaseYear)
        ORDER BY lower(m.title) COLLATE "und-x-icu" ASC, m.id ASC
        """,
        countQuery = """
        SELECT COUNT(*) FROM movie m
        WHERE (:genreCode IS NULL OR EXISTS (
            SELECT 1 FROM movie_genre mg
            WHERE mg.movie_id = m.id AND mg.genre_code = :genreCode
        ))
        AND (:releaseYear IS NULL OR EXTRACT(YEAR FROM m.release_date) = :releaseYear)
        """,
        nativeQuery = true,
    )
    fun findAllByFilter(
        @Param("genreCode") genreCode: String?,
        @Param("releaseYear") releaseYear: Int?,
        pageable: Pageable,
    ): Page<Movie>

    /**
     * Count of movies sorting before [letter] within the same genre/release-year
     * filter as [findAllByFilter] (alphabet-jump pagination, V2.4). Same
     * ICU-collated comparison and native-SQL rationale as [findAllByFilter].
     */
    @Query(
        value = """
        SELECT COUNT(*) FROM movie m
        WHERE lower(m.title) COLLATE "und-x-icu" < lower(:letter) COLLATE "und-x-icu"
        AND (:genreCode IS NULL OR EXISTS (
            SELECT 1 FROM movie_genre mg
            WHERE mg.movie_id = m.id AND mg.genre_code = :genreCode
        ))
        AND (:releaseYear IS NULL OR EXTRACT(YEAR FROM m.release_date) = :releaseYear)
        """,
        nativeQuery = true,
    )
    fun countTitlesBefore(
        @Param("letter") letter: String,
        @Param("genreCode") genreCode: String?,
        @Param("releaseYear") releaseYear: Int?,
    ): Long
}

interface MovieGenreRepository : JpaRepository<MovieGenre, MovieGenreId> {
    fun findAllByIdMovieId(movieId: UUID): List<MovieGenre>
    fun findAllByIdMovieIdIn(movieIds: Collection<UUID>): List<MovieGenre>
    fun deleteByIdMovieId(movieId: UUID)
}
