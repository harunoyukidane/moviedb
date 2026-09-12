package com.moviecatalogue.people.domain

/**
 * Builds a safe SQL LIKE pattern from a user query. The user's literal `%`, `_`
 * and the escape char `\` are escaped so they match literally rather than acting
 * as wildcards; the whole term is then wrapped in `%...%` for a substring match.
 *
 * Must be used together with `ESCAPE '\'` in the query (see PersonRepository).
 */
object PersonSearch {
    /** Escape LIKE metacharacters in [term]; does NOT add surrounding wildcards. */
    fun escapeLike(term: String): String {
        val sb = StringBuilder(term.length + 8)
        for (c in term) {
            when (c) {
                '\\', '%', '_' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Escape [term] and wrap it for a case-insensitive substring (contains) match. */
    fun containsPattern(term: String): String = "%" + escapeLike(term) + "%"
}
