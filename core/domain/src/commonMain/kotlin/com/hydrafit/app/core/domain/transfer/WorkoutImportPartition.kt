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

    companion object {
        private const val VERSION = "workout-partition-v1"

        /**
         * Reads back a [canonical] manifest, or returns null when the encoding is malformed or its
         * membership does not cover the bucket exactly once. A manifest that cannot be read back is
         * treated as an unusable record rather than trusted, so a corrupt stored partition can never
         * be reused as if it were the approved one.
         */
        fun decode(encoded: String): WorkoutImportPartition? {
            val outer = decodeCanonicalFields(encoded) ?: return null
            if (outer.size != 4 || outer[0] != VERSION) return null
            val rowCount = outer[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            val sessionFields = decodeCanonicalFields(outer[2]) ?: return null
            if (sessionFields.isEmpty()) return null
            val sessions = sessionFields.map { field ->
                val positions = decodeCanonicalFields(field) ?: return null
                if (positions.isEmpty()) return null
                positions.map { it.toIntOrNull() ?: return null }
            }
            val excludedFields = decodeCanonicalFields(outer[3]) ?: return null
            val excluded = excludedFields.map { it.toIntOrNull() ?: return null }.toSet()
            val partition = WorkoutImportPartition(rowCount, sessions, excluded)
            val assigned = sessions.flatten() + excluded
            if (assigned.size != rowCount || assigned.toSet() != (0 until rowCount).toSet()) {
                return null
            }
            return partition
        }
    }
}
