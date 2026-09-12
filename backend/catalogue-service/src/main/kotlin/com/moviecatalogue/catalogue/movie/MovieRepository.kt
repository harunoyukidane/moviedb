package com.moviecatalogue.catalogue.movie

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MovieRepository : JpaRepository<Movie, UUID> {
    /** Paged listing ordered by the caller-supplied Pageable (title asc by default). */
    override fun findAll(pageable: Pageable): Page<Movie>
}

interface MovieGenreRepository : JpaRepository<MovieGenre, MovieGenreId> {
    fun findAllByIdMovieId(movieId: UUID): List<MovieGenre>
    fun findAllByIdMovieIdIn(movieIds: Collection<UUID>): List<MovieGenre>
    fun deleteByIdMovieId(movieId: UUID)
}
