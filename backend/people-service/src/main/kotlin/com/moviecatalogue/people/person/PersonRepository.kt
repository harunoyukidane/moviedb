package com.moviecatalogue.people.person

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Outbound port for people persistence (Spring Data JPA adapter).
 *
 * Search matches on `lower(name)` with a `%term%` substring pattern (§7.3),
 * accelerated by the GIN trigram index `ix_person_name_trgm` (V2.5-01)
 * rather than the B-tree `ix_person_name_lower`, which only helps prefix
 * matches. The caller passes a LIKE pattern whose user-supplied `%`, `_` and
 * `\` have already been escaped (see [PersonSearch]); `ESCAPE '\'` makes
 * those literals rather than wildcards.
 */
interface PersonRepository : JpaRepository<Person, UUID> {

    fun findAllByIdIn(ids: Collection<UUID>): List<Person>

    fun existsByTmdbId(tmdbId: Long): Boolean

    /**
     * Ordered by `lower(name) COLLATE "und-x-icu"` (matches [findAllOrderByName]/
     * [countByNameLessThan]/[countByNamePatternLessThan]) so an accented name
     * interleaves with its base letter instead of trailing after every ASCII
     * name (this DB's default libc collation compares by raw code point).
     * Native SQL because JPQL/HQL's `collate()` function only accepts a
     * bare-identifier collation name, and "und-x-icu" isn't one (see V4
     * migration for the matching index).
     */
    @Query(
        value = """
            SELECT * FROM person p
            WHERE lower(p.name) LIKE lower(:pattern) ESCAPE '\'
            ORDER BY lower(p.name) COLLATE "und-x-icu" ASC, p.id ASC
        """,
        nativeQuery = true,
    )
    fun searchByNamePattern(@Param("pattern") pattern: String, pageable: Pageable): List<Person>

    /**
     * Paged listing of all people (blank query = list all). Same ICU-collated
     * ordering and native-SQL rationale as [searchByNamePattern].
     */
    @Query(
        value = """
            SELECT * FROM person p
            ORDER BY lower(p.name) COLLATE "und-x-icu" ASC, p.id ASC
        """,
        nativeQuery = true,
    )
    fun findAllOrderByName(pageable: Pageable): List<Person>

    @Query(
        value = """
            SELECT count(p) FROM Person p
            WHERE lower(p.name) LIKE lower(:pattern) ESCAPE '\'
        """,
    )
    fun countByNamePattern(@Param("pattern") pattern: String): Long

    /**
     * Count of all people sorting before [letter] (alphabet-jump pagination,
     * blank query). Same ICU-collated comparison and native-SQL rationale as
     * [searchByNamePattern].
     */
    @Query(
        value = """
            SELECT count(*) FROM person p
            WHERE lower(p.name) COLLATE "und-x-icu" < lower(:letter) COLLATE "und-x-icu"
        """,
        nativeQuery = true,
    )
    fun countByNameLessThan(@Param("letter") letter: String): Long

    /**
     * Count of people matching [pattern] that also sort before [letter]
     * (alphabet-jump pagination, active search). Same ICU-collated comparison
     * and native-SQL rationale as [searchByNamePattern].
     */
    @Query(
        value = """
            SELECT count(*) FROM person p
            WHERE lower(p.name) LIKE lower(:pattern) ESCAPE '\'
              AND lower(p.name) COLLATE "und-x-icu" < lower(:letter) COLLATE "und-x-icu"
        """,
        nativeQuery = true,
    )
    fun countByNamePatternLessThan(@Param("pattern") pattern: String, @Param("letter") letter: String): Long

    /** Storage keys currently referenced by person profile metadata. */
    @Query("SELECT p.profilePath FROM Person p WHERE p.profilePath IS NOT NULL")
    fun findAllProfilePaths(): List<String>
}
