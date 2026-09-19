package com.moviecatalogue.catalogue.application

import com.moviecatalogue.catalogue.credit.CreditRepository
import com.moviecatalogue.catalogue.domain.MovieRules
import com.moviecatalogue.catalogue.domain.SearchPattern
import com.moviecatalogue.catalogue.domain.ValidationException
import com.moviecatalogue.catalogue.movie.Movie
import com.moviecatalogue.catalogue.movie.MovieRepository
import com.moviecatalogue.catalogue.people.PeopleClient
import com.moviecatalogue.catalogue.people.PersonHit
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** A movie hit, with the names of matched people whose credits surfaced it (§9). */
data class MovieSearchHit(
    val id: UUID,
    val title: String,
    val releaseDate: java.time.LocalDate?,
    val matchedPersonNames: List<String>,
    val rank: Int,
)

data class PersonSearchHit(val id: UUID, val name: String)

data class SearchResultView(
    val movies: List<MovieSearchHit>,
    val people: List<PersonSearchHit>,
)

/**
 * Unified search (§9). Within one bounded request:
 * 1. Catalogue searches movie title / original title (escaped, indexed).
 * 2. People Service searches names via gRPC (concurrently).
 * 3. Catalogue finds movies credited to the matched people (ix_credit_person)
 *    and attaches `matchedPersonNames`.
 * Results are de-duplicated and ranked: exact-prefix title/name, then substring,
 * then related-credit-only. Blank queries are rejected consistently; user
 * wildcards are escaped so they match literally (§14).
 */
@Service
class SearchUseCases(
    private val movies: MovieRepository,
    private val credits: CreditRepository,
    private val peopleClient: PeopleClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Ranking tiers (lower = higher priority).
    private companion object {
        const val RANK_EXACT_PREFIX = 0
        const val RANK_SUBSTRING = 1
        const val RANK_RELATED_CREDIT = 2

        /**
         * Deep-offset ceiling for unified search only (V2.7-01). Unlike
         * movies/people, search still hydrates `offset + limit` rows in memory to
         * merge and rank title and credit hits, so a request at or past this
         * offset returns an empty page without touching the DB or gRPC at all,
         * rather than clamping into `clampOffset` (which would silently change
         * movies/people semantics too). Set well above any offset a real paging
         * UI reaches — page 100 at the max limit of 100/page — while still
         * bounding the worst-case in-memory hydration.
         */
        const val MAX_SEARCH_OFFSET = 10_000
    }

    @Transactional(readOnly = true)
    fun search(rawQuery: String, limit: Int, offset: Int): SearchResultView {
        val query = normalizeQuery(rawQuery)
        val clampedLimit = MovieRules.clampLimit(limit)
        val clampedOffset = MovieRules.clampOffset(offset)
        if (clampedOffset >= MAX_SEARCH_OFFSET) {
            return SearchResultView(emptyList(), emptyList())
        }
        val lower = query.lowercase()
        val pattern = SearchPattern.containsPattern(query)

        // 1 & 2 concurrently: title search (DB) + people search (gRPC).
        // Math.addExact: clampedOffset < MAX_SEARCH_OFFSET and clampedLimit <=
        // PAGE_LIMIT_MAX so this never actually overflows, but it guarantees the
        // int overflow -> negative PageRequest -> INTERNAL_ERROR path (V2.7-01)
        // can never reopen if either bound is loosened later.
        val fetchWindow = Math.addExact(clampedOffset, clampedLimit)
        val titleFuture = CompletableFuture.supplyAsync {
            movies.searchByTitlePattern(pattern, PageRequest.of(0, fetchWindow))
        }
        val peopleFuture = CompletableFuture.supplyAsync {
            // people outage must not fail the whole search (§13): degrade to empty.
            runCatching { peopleClient.searchPeople(query, fetchWindow, 0) }.getOrElse {
                log.warn("people search degraded: {}", it.message)
                emptyList()
            }
        }

        val titleMovies: List<Movie> = titleFuture.join()
        val peopleHits: List<PersonHit> = peopleFuture.join()

        // 3. person -> movie credited traversal (ix_credit_person).
        val personNameById = peopleHits.associate { it.id to it.name }
        val relatedByMovie: MutableMap<UUID, MutableSet<String>> = HashMap()
        if (peopleHits.isNotEmpty()) {
            for (credit in credits.findAllByPersonIdIn(personNameById.keys)) {
                val name = personNameById[credit.personId] ?: continue
                relatedByMovie.getOrPut(credit.movieId) { linkedSetOf() }.add(name)
            }
        }

        // Assemble movie hits with rank, de-duplicating by movie id.
        val hitById = LinkedHashMap<UUID, MovieSearchHit>()

        // title matches first (exact-prefix vs substring)
        for (m in titleMovies) {
            val title = m.title.lowercase()
            val orig = m.originalTitle?.lowercase()
            val rank = if (title.startsWith(lower) || (orig?.startsWith(lower) == true)) {
                RANK_EXACT_PREFIX
            } else {
                RANK_SUBSTRING
            }
            hitById[m.id] = MovieSearchHit(
                id = m.id, title = m.title, releaseDate = m.releaseDate,
                matchedPersonNames = relatedByMovie[m.id]?.toList() ?: emptyList(),
                rank = rank,
            )
        }

        // related-credit movies not already surfaced by title need loading
        val missingRelated = relatedByMovie.keys - hitById.keys
        if (missingRelated.isNotEmpty()) {
            for (m in movies.findAllById(missingRelated)) {
                hitById[m.id] = MovieSearchHit(
                    id = m.id, title = m.title, releaseDate = m.releaseDate,
                    matchedPersonNames = relatedByMovie[m.id]?.toList() ?: emptyList(),
                    rank = RANK_RELATED_CREDIT,
                )
            }
        } else {
            // a title match that ALSO has related credits keeps its stronger title rank
            // (already set above); nothing to do.
        }

        val rankedMovies = hitById.values
            .sortedWith(compareBy({ it.rank }, { it.title.lowercase() }, { it.id }))
            .drop(clampedOffset)
            .take(clampedLimit)

        // People hits: exact-prefix names before substring.
        val rankedPeople = peopleHits
            .sortedWith(
                compareBy(
                    { if (it.name.lowercase().startsWith(lower)) 0 else 1 },
                    { it.name.lowercase() },
                ),
            )
            .map { PersonSearchHit(it.id, it.name) }
            .drop(clampedOffset)
            .take(clampedLimit)

        return SearchResultView(rankedMovies, rankedPeople)
    }

    /** Blank/whitespace queries are rejected (consistent rule); length 1..100. */
    private fun normalizeQuery(raw: String?): String {
        val q = raw?.trim().orEmpty()
        if (q.isEmpty()) throw ValidationException("search query must not be blank", field = "query")
        if (q.length > 100) throw ValidationException("search query must be at most 100 characters", field = "query")
        return q
    }
}
