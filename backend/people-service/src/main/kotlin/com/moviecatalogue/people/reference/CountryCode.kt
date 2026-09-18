package com.moviecatalogue.people.reference

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** Controlled ISO 3166-1 alpha-2 country code (V2.2-10). Reference data seeded via Flyway; read-only here. */
@Entity
@Table(name = "country_code")
class CountryCode(
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
