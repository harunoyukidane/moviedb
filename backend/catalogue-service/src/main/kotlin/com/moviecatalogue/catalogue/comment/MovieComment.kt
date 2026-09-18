package com.moviecatalogue.catalogue.comment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * JPA mapping of `movie_comment` (V2-13). Append-only for user comments, which
 * have no update path and are physically removed only via the movie's cascade
 * delete. `authorDisplayName`/`text` stay updatable at the JPA level solely so
 * the importer can upsert a seeded row in place by `seedKey` (V2.2-13) - a
 * user-submitted comment never carries a seed key, so it never reaches that
 * code path and is unaffected.
 */
@Entity
@Table(name = "movie_comment")
class MovieComment(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "movie_id", nullable = false, updatable = false)
    var movieId: UUID,

    @Column(name = "author_display_name", nullable = false)
    var authorDisplayName: String,

    @Column(name = "text", nullable = false)
    var text: String,

    /** App-generated (never trust a client timestamp); the DB default only backstops other insert paths. */
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime,

    /**
     * Importer idempotency key (V2.2-13), e.g. `seed:<tmdbId>:<n>`. Null for
     * every user-submitted comment; a partial unique index enforces uniqueness
     * only among non-null values.
     */
    @Column(name = "seed_key")
    var seedKey: String? = null,
)
