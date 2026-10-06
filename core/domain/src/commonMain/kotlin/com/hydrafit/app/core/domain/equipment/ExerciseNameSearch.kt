package com.hydrafit.app.core.domain.equipment

private val SEARCH_SEPARATOR = Regex("[\\s\\u002D\\u2010-\\u2015]+")

/**
 * Search-only normalization: trims and treats any run of whitespace or dash characters as a single
 * separator so a space-separated query can match a hyphenated name and vice versa. Case is left to
 * the caller's `ignoreCase` matching. It never affects stored names, ids, or deduplication identity.
 */
internal fun normalizeExerciseSearchText(text: String): String =
    text.trim().replace(SEARCH_SEPARATOR, " ").trim()

/**
 * True when [name] matches [query] under search-only normalization. A query that normalizes to empty
 * matches everything, so a separator-only query shows the full list rather than nothing.
 */
fun matchesExerciseNameQuery(name: String, query: String): Boolean {
    val normalizedQuery = normalizeExerciseSearchText(query)
    return normalizedQuery.isEmpty() ||
        normalizeExerciseSearchText(name).contains(normalizedQuery, ignoreCase = true)
}
