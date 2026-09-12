package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.credit.MovieCredit
import com.moviecatalogue.catalogue.domain.CreditCategory
import com.moviecatalogue.catalogue.domain.DependencyUnavailableException
import com.moviecatalogue.catalogue.people.PeopleClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the person reference for a set of credits in a single batched gRPC
 * call (§6.5, no N+1). If the People Service is unavailable or a person is
 * missing, the reference is marked `available = false` rather than failing the
 * whole page (§13). The correlation-aware logging lives in the adapter/boundary.
 */
@Component
class PersonHydrator(
    private val peopleClient: PeopleClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Batch-resolve [personIds] to a name+availability map. On dependency failure,
     * returns all-unavailable rather than throwing, so a movie page still renders.
     */
    fun resolve(personIds: Collection<UUID>): Map<UUID, PersonRef> {
        val distinct = personIds.toSet()
        if (distinct.isEmpty()) return emptyMap()
        val people = try {
            peopleClient.getPeople(distinct)
        } catch (e: DependencyUnavailableException) {
            log.warn("degraded person hydration: {}", e.message)
            emptyMap()
        }
        return distinct.associateWith { id ->
            val data = people[id]
            if (data != null) PersonRef(id, data.name, available = true)
            else PersonRef(id, name = "", available = false)
        }
    }

    /** Map a batch of credits to CreditViews, hydrating all their people in one call. */
    fun toCreditViews(credits: List<MovieCredit>): List<CreditView> {
        val refs = resolve(credits.map { it.personId })
        return credits.map { it.toView(refs.getValue(it.personId)) }
    }
}

fun MovieCredit.toView(person: PersonRef): CreditView = CreditView(
    id = id,
    movieId = movieId,
    category = category,
    roleCode = roleCode,
    characterName = characterName,
    sourceRoleName = sourceRoleName,
    billingOrder = billingOrder,
    person = person,
)
