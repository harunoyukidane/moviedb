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
}

interface MovieGenreRepository : JpaRepository<MovieGenre, MovieGenreId> {
    fun findAllByIdMovieId(movieId: UUID): List<MovieGenre>
    fun findAllByIdMovieIdIn(movieIds: Collection<UUID>): List<MovieGenre>
    fun deleteByIdMovieId(movieId: UUID)
}
