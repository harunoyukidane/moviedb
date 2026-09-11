package com.moviecatalogue.people.person

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PersonRepository : JpaRepository<Person, UUID> {
    fun findAllByIdIn(ids: Collection<UUID>): List<Person>
    fun existsByTmdbId(tmdbId: Long): Boolean
}
