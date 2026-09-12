package com.moviecatalogue.catalogue.domain

/**
 * Builds safe SQL LIKE patterns from a user query, escaping the LIKE
 * metacharacters `%`, `_`, and the escape char `\` so user input is matched
 * literally (§14). Used with `ESCAPE '\'` in the query.
 */
object SearchPattern {
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

    /** Escaped substring (contains) pattern: %term%. */
    fun containsPattern(term: String): String = "%" + escapeLike(term) + "%"

    /** Escaped prefix pattern: term%. */
    fun prefixPattern(term: String): String = escapeLike(term) + "%"
}
