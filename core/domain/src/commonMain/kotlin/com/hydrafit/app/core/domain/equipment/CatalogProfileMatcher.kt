package com.hydrafit.app.core.domain.equipment

sealed interface CatalogProfileMatch {
    data object Unknown : CatalogProfileMatch
    data class Unique(val candidate: CatalogExerciseProfile) : CatalogProfileMatch
    data class Ambiguous(val candidates: List<CatalogExerciseProfile>) : CatalogProfileMatch
}

/** Exact lexical matching only, using the same separators as exercise search, not Save identity. */
object CatalogProfileMatcher {
    fun match(name: String, snapshot: List<CatalogExerciseProfile>): CatalogProfileMatch {
        val query = normalizeExerciseSearchText(name)
        if (query.isEmpty()) return CatalogProfileMatch.Unknown
        val candidates = snapshot.filter { candidate ->
            candidate.matchingLabels.any { label ->
                normalizeExerciseSearchText(label).equals(query, ignoreCase = true)
            }
        }.distinctBy { it.catalogId }.sortedBy { it.catalogId }
        return when (candidates.size) {
            0 -> CatalogProfileMatch.Unknown
            1 -> CatalogProfileMatch.Unique(candidates.single())
            else -> CatalogProfileMatch.Ambiguous(candidates)
        }
    }
}
