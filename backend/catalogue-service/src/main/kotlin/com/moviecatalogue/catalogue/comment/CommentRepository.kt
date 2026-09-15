package com.moviecatalogue.catalogue.comment

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommentRepository : JpaRepository<MovieComment, UUID> {
    /**
     * Reverse-chronological page of comments for one movie
     * (ix_movie_comment_movie_created), id descending as a stable tie-breaker.
     */
    fun findAllByMovieIdOrderByCreatedAtDescIdDesc(movieId: UUID, pageable: Pageable): Page<MovieComment>
}
