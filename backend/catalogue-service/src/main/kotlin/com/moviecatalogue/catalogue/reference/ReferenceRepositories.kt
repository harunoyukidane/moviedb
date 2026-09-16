package com.moviecatalogue.catalogue.reference

import com.moviecatalogue.catalogue.domain.CreditCategory
import org.springframework.data.jpa.repository.JpaRepository

interface GenreCodeRepository : JpaRepository<GenreCode, String> {
    fun findAllByActiveTrueOrderByDisplayOrderAsc(): List<GenreCode>
    fun findAllByOrderByDisplayOrderAsc(): List<GenreCode>
}

interface CreditRoleCodeRepository : JpaRepository<CreditRoleCode, String> {
    fun findAllByActiveTrueOrderByDisplayOrderAsc(): List<CreditRoleCode>
    fun findAllByOrderByDisplayOrderAsc(): List<CreditRoleCode>
    fun findAllByCategoryOrderByDisplayOrderAsc(category: CreditCategory): List<CreditRoleCode>
    fun findAllByCategoryAndActiveTrueOrderByDisplayOrderAsc(category: CreditCategory): List<CreditRoleCode>
}

interface LanguageCodeRepository : JpaRepository<LanguageCode, String> {
    fun findAllByActiveTrueOrderByDisplayOrderAsc(): List<LanguageCode>
    fun findAllByOrderByDisplayOrderAsc(): List<LanguageCode>
}
