package com.moviecatalogue.catalogue.credit

import com.moviecatalogue.catalogue.domain.CreditCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

/**
 * JPA mapping of `movie_credit` (§7.2). A credit references a person only by ID
 * (person data is owned by the People Service, ADR-1). The DB enforces the
 * cast/crew character check, composite (role_code, category) FK, and uniqueness;
 * the domain rules pre-validate the same invariants.
 */
@Entity
@Table(name = "movie_credit")
class MovieCredit(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID,

    @Column(name = "movie_id", nullable = false)
    var movieId: UUID,

    @Column(name = "person_id", nullable = false)
    var personId: UUID,

    @Column(name = "role_code", nullable = false)
    var roleCode: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false, columnDefinition = "credit_category")
    var category: CreditCategory,

    @Column(name = "character_name")
    var characterName: String? = null,

    @Column(name = "source_role_name")
    var sourceRoleName: String? = null,

    @Column(name = "billing_order")
    var billingOrder: Int? = null,

    @Column(name = "tmdb_credit_id")
    var tmdbCreditId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    var createdAt: OffsetDateTime? = null,

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: OffsetDateTime? = null,
)
