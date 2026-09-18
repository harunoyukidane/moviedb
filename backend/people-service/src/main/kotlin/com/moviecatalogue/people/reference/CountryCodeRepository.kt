package com.moviecatalogue.people.reference

import org.springframework.data.jpa.repository.JpaRepository

interface CountryCodeRepository : JpaRepository<CountryCode, String> {
    fun findAllByActiveTrueOrderByDisplayOrderAsc(): List<CountryCode>
    fun findAllByOrderByDisplayOrderAsc(): List<CountryCode>
}
