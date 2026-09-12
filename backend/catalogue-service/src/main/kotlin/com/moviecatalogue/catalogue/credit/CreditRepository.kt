package com.moviecatalogue.catalogue.credit

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditRepository : JpaRepository<MovieCredit, UUID> {
    /** Credits for one movie (used for a single movie's cast/creators). */
    fun findAllByMovieId(movieId: UUID): List<MovieCredit>

    /** Credits across a page of movies (used to batch person hydration). */
    fun findAllByMovieIdIn(movieIds: Collection<UUID>): List<MovieCredit>

    /** All credits referencing a person (used for safe delete + Person.credits). */
    fun findAllByPersonId(personId: UUID): List<MovieCredit>

    /** Reference-protection check for person delete (§6.6). */
    fun existsByPersonId(personId: UUID): Boolean
}
