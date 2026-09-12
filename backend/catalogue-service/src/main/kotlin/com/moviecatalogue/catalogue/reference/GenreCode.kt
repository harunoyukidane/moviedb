package com.moviecatalogue.catalogue.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Controlled genre code (§7.2). Reference data seeded via Flyway; read-only here. */
@Entity
@Table(name = "genre_code")
class GenreCode(
    @Id
    @Column(name = "code", nullable = false, updatable = false)
    var code: String,

    @Column(name = "tmdb_id")
    var tmdbId: Long? = null,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "description", nullable = false)
    var description: String,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
