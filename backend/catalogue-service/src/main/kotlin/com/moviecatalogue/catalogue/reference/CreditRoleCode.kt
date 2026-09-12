package com.moviecatalogue.catalogue.reference

import com.moviecatalogue.catalogue.domain.CreditCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Controlled credit-role code (§7.2). `category` maps to the Postgres
 * `credit_category` enum; JdbcTypeCode NAMED_ENUM binds the Kotlin enum to it.
 */
@Entity
@Table(name = "credit_role_code")
class CreditRoleCode(
    @Id
    @Column(name = "code", nullable = false, updatable = false)
    var code: String,

    @Column(name = "title", nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "category", nullable = false, columnDefinition = "credit_category")
    var category: CreditCategory,

    @Column(name = "department")
    var department: String? = null,

    @Column(name = "description", nullable = false)
    var description: String,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)
