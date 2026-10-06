package com.hydrafit.app.core.domain.fatigue

/**
 * Essential ledger columns from the archived BACK investigation (`docs/plans-archive.md`);
 * 39 working sets and three excluded warm-ups.
 */
internal object FatigueReplayFixture {
    const val EVALUATION_MILLIS = 1790873936000L
    const val PEAK_MILLIS = 1790844708670L

    private enum class SetType { COMPOUND, ISOLATION }

    private data class Row(
        val timestampMillis: Long,
        val reps: Int,
        val backWeight: Double,
        val isWarmup: Boolean = false,
        val type: SetType = SetType.ISOLATION
    )

    private val rows = listOf(
        row(1790787141874, 20, 0.3, type = SetType.COMPOUND), // Dumbbell Lunge
        row(1790787142631, 20, 0.3, type = SetType.COMPOUND),
        row(1790787143031, 20, 0.3, type = SetType.COMPOUND),
        row(1790787143323, 20, 0.3, type = SetType.COMPOUND),
        row(1790787537730, 8, 0.3), // Leg Extension
        row(1790787538221, 8, 0.3),
        row(1790787538587, 8, 0.3),
        row(1790787542344, 8, 0.3),
        row(1790839959646, 8, 1.0, isWarmup = true, type = SetType.COMPOUND), // Chin-up
        row(1790840075113, 8, 1.0, type = SetType.COMPOUND),
        row(1790840172676, 8, 1.0, type = SetType.COMPOUND),
        row(1790840464767, 8, 1.0, isWarmup = true, type = SetType.COMPOUND), // Trap Bar Deadlift
        row(1790840544775, 8, 1.0, type = SetType.COMPOUND),
        row(1790840847553, 8, 1.0, type = SetType.COMPOUND),
        row(1790840853110, 6, 1.0, type = SetType.COMPOUND),
        row(1790840961215, 2, 1.0, type = SetType.COMPOUND),
        row(1790840966189, 1, 1.0, type = SetType.COMPOUND),
        row(1790841004989, 10, 1.0, type = SetType.COMPOUND), // Lat Pulldown
        row(1790841188246, 8, 1.0, type = SetType.COMPOUND),
        row(1790841471413, 8, 1.0, type = SetType.COMPOUND),
        row(1790841949887, 8, 1.0, type = SetType.COMPOUND),
        row(1790841975016, 8, 1.0, type = SetType.COMPOUND), // Seated Cable Row
        row(1790842215182, 8, 1.0, type = SetType.COMPOUND),
        row(1790842342522, 8, 1.0, type = SetType.COMPOUND),
        row(1790842517018, 10, 1.0, type = SetType.COMPOUND),
        row(1790842632912, 10, 0.3, isWarmup = true, type = SetType.COMPOUND), // Shoulder Press
        row(1790842775294, 10, 0.3, type = SetType.COMPOUND),
        row(1790842907722, 10, 0.5, type = SetType.COMPOUND), // Upright Row
        row(1790842910996, 10, 0.5, type = SetType.COMPOUND),
        row(1790842916068, 8, 0.5, type = SetType.COMPOUND),
        row(1790842921428, 6, 0.5, type = SetType.COMPOUND),
        row(1790843013812, 10, 0.3, type = SetType.COMPOUND), // Shoulder Press
        row(1790843343214, 8, 0.3, type = SetType.COMPOUND),
        row(1790843811620, 6, 0.3, type = SetType.COMPOUND),
        row(1790843906097, 10, 0.5), // Raise Combo
        row(1790843910347, 8, 0.5),
        row(1790844220908, 8, 0.5),
        row(1790844225083, 8, 0.5),
        row(1790844413048, 10, 1.0), // Face Pull
        row(1790844419279, 10, 1.0),
        row(1790844557801, 8, 1.0),
        row(1790844708670, 8, 1.0)
    )

    /** Phase B replay: exercise type is unknown, so every set is treated as isolation. */
    val sets: List<LoggedSet> = rows.map { it.toLoggedSet(isCompound = false) }

    /** Informational replay with the original redesign's exercise types applied. */
    val typedSets: List<LoggedSet> =
        rows.map { it.toLoggedSet(isCompound = it.type == SetType.COMPOUND) }

    /** The typed replay with one RIR value applied to every set, for the C3 replay. */
    fun typedSetsWithRir(rir: Int?): List<LoggedSet> = typedSets.map { it.copy(rir = rir) }

    /**
     * The replay rows with the two S2 backfill session ids applied: the first session's working
     * block (a), then everything from the second session's first set onward (b, warm-ups included).
     */
    fun withBackfillSessionIds(sets: List<LoggedSet>): List<LoggedSet> = sets.map { set ->
        val id = if (set.timestampMillis <= FIRST_SESSION_LAST_MILLIS) {
            "backfill-a"
        } else {
            "backfill-b"
        }
        set.copy(sessionId = id)
    }

    /** Last performed-at of the first backfilled session; the next row begins session b. */
    private const val FIRST_SESSION_LAST_MILLIS = 1790787542344L

    private fun row(
        timestampMillis: Long,
        reps: Int,
        backWeight: Double,
        isWarmup: Boolean = false,
        type: SetType = SetType.ISOLATION
    ) = Row(timestampMillis, reps, backWeight, isWarmup, type)

    private fun Row.toLoggedSet(isCompound: Boolean) = LoggedSet(
        timestampMillis = timestampMillis,
        targets = listOf(MuscleTarget(MuscleGroup.LATS, backWeight)),
        isWarmup = isWarmup,
        reps = reps,
        isCompound = isCompound
    )
}
