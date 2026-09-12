package com.moviecatalogue.people.person

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Outbound port for people persistence (Spring Data JPA adapter).
 *
 * Search matches on `lower(name)` so it can use the `ix_person_name_lower`
 * functional index (§7.3). The caller passes a LIKE pattern whose user-supplied
 * `%`, `_` and `\` have already been escaped (see [PersonSearch]); `ESCAPE '\'`
 * makes those literals rather than wildcards.
 */
interface PersonRepository : JpaRepository<Person, UUID> {

    fun findAllByIdIn(ids: Collection<UUID>): List<Person>

    fun existsByTmdbId(tmdbId: Long): Boolean

    @Query(
        value = """
            SELECT p FROM Person p
            WHERE lower(p.name) LIKE lower(:pattern) ESCAPE '\'
            ORDER BY lower(p.name) ASC, p.id ASC
        """,
    )
    fun searchByNamePattern(@Param("pattern") pattern: String, pageable: Pageable): List<Person>

    @Query(
        value = """
            SELECT count(p) FROM Person p
            WHERE lower(p.name) LIKE lower(:pattern) ESCAPE '\'
        """,
    )
    fun countByNamePattern(@Param("pattern") pattern: String): Long
}
