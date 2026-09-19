package com.moviecatalogue.people.person

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * JPA mapping of the `person` table (§7.3). The `id` is an application-supplied
 * UUIDv7 (ADR-10). `version` provides optimistic locking.
 */
@Entity
@Table(name = "person")
class Person(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "tmdb_id")
    var tmdbId: Long? = null,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "biography", nullable = false)
    var biography: String = "",

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "death_date")
    var deathDate: LocalDate? = null,

    @Column(name = "place_of_birth")
    var placeOfBirth: String? = null,

    @Column(name = "birth_country_code")
    var birthCountryCode: String? = null,

    @Column(name = "profile_path")
    var profilePath: String? = null,

    /** Optional WebP variant for `Accept`-negotiated serving; null if `cwebp` wasn't available at upload time. */
    @Column(name = "profile_path_webp")
    var profilePathWebp: String? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null,
)
