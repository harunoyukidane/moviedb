package com.moviecatalogue.catalogue.comment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * JPA mapping of `movie_comment` (V2-13). Append-only: comments have no
 * update path and are physically removed only via the movie's cascade delete.
 */
@Entity
@Table(name = "movie_comment")
class MovieComment(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "movie_id", nullable = false, updatable = false)
    var movieId: UUID,

    @Column(name = "author_display_name", nullable = false, updatable = false)
    var authorDisplayName: String,

    @Column(name = "text", nullable = false, updatable = false)
    var text: String,

    /** App-generated (never trust a client timestamp); the DB default only backstops other insert paths. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime,
)
