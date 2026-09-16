package com.moviecatalogue.catalogue.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Controlled ISO 639-1 language code (v2.1). Reference data seeded via Flyway; read-only here. */
@Entity
@Table(name = "language_code")
class LanguageCode(
    @Id
    @Column(name = "code", nullable = false, updatable = false)
    var code: String,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
