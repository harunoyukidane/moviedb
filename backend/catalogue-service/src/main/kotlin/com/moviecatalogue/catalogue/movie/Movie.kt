package com.moviecatalogue.catalogue.movie

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Minimal JPA mapping of the `movie` table (§7.2) for phase-1 integration
 * tests (optimistic locking, cascade). Full mapping and related aggregates
 * arrive in phase 3. `id` is an application-supplied UUIDv7 (ADR-10).
 */
@Entity
@Table(name = "movie")
class Movie(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "tmdb_id")
    var tmdbId: Long? = null,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "original_title")
    var originalTitle: String? = null,

    @Column(name = "synopsis", nullable = false)
    var synopsis: String = "",

    @Column(name = "release_date")
    var releaseDate: LocalDate? = null,

    @Column(name = "runtime_minutes")
    var runtimeMinutes: Int? = null,

    @Column(name = "original_language")
    var originalLanguage: String? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null,
)
