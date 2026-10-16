package com.hydrafit.app.core.domain.transfer

/**
 * Durable interpretation of one provider/date/title bucket. Positions are relative to the complete
 * bucket in source order, never CSV line numbers. Every position belongs to exactly one session or
 * to the explicit exclusions, including repeated identical source rows.
 */
data class WorkoutImportPartition(
    val rowCount: Int,
    val sessions: List<List<Int>>,
    val excluded: Set<Int>
) {
    /** A manifest contains membership only; local times, mappings and history edits are not identity. */
    fun canonical(): String {
        require(rowCount > 0)
        require(sessions.isNotEmpty() && sessions.all { it.isNotEmpty() })
        val assigned = sessions.flatten() + excluded
        require(assigned.size == rowCount && assigned.toSet() == (0 until rowCount).toSet())
        return canonicalFields(
            listOf(
                "workout-partition-v1",
                rowCount.toString(),
                canonicalFields(
                    sessions.map { positions ->
                        canonicalFields(positions.sorted().map(Int::toString))
                    }
                ),
                canonicalFields(excluded.sorted().map(Int::toString))
            )
        )
    }
}
