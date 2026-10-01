package com.hydrafit.app.core.domain.fatigue

/** Essential ledger columns from PLANS.md; 39 working sets and three excluded warm-ups. */
internal object FatigueReplayFixture {
    const val EVALUATION_MILLIS = 1790873936000L
    const val PEAK_MILLIS = 1790844708670L

    val sets: List<LoggedSet> = listOf(
        row(1790787141874, 20, 0.3),
        row(1790787142631, 20, 0.3),
        row(1790787143031, 20, 0.3),
        row(1790787143323, 20, 0.3),
        row(1790787537730, 8, 0.3),
        row(1790787538221, 8, 0.3),
        row(1790787538587, 8, 0.3),
        row(1790787542344, 8, 0.3),
        row(1790839959646, 8, 1.0, isWarmup = true),
        row(1790840075113, 8, 1.0),
        row(1790840172676, 8, 1.0),
        row(1790840464767, 8, 1.0, isWarmup = true),
        row(1790840544775, 8, 1.0),
        row(1790840847553, 8, 1.0),
        row(1790840853110, 6, 1.0),
        row(1790840961215, 2, 1.0),
        row(1790840966189, 1, 1.0),
        row(1790841004989, 10, 1.0),
        row(1790841188246, 8, 1.0),
        row(1790841471413, 8, 1.0),
        row(1790841949887, 8, 1.0),
        row(1790841975016, 8, 1.0),
        row(1790842215182, 8, 1.0),
        row(1790842342522, 8, 1.0),
        row(1790842517018, 10, 1.0),
        row(1790842632912, 10, 0.3, isWarmup = true),
        row(1790842775294, 10, 0.3),
        row(1790842907722, 10, 0.5),
        row(1790842910996, 10, 0.5),
        row(1790842916068, 8, 0.5),
        row(1790842921428, 6, 0.5),
        row(1790843013812, 10, 0.3),
        row(1790843343214, 8, 0.3),
        row(1790843811620, 6, 0.3),
        row(1790843906097, 10, 0.5),
        row(1790843910347, 8, 0.5),
        row(1790844220908, 8, 0.5),
        row(1790844225083, 8, 0.5),
        row(1790844413048, 10, 1.0),
        row(1790844419279, 10, 1.0),
        row(1790844557801, 8, 1.0),
        row(1790844708670, 8, 1.0)
    )

    private fun row(
        timestampMillis: Long,
        reps: Int,
        backWeight: Double,
        isWarmup: Boolean = false
    ) = LoggedSet(
        timestampMillis = timestampMillis,
        targets = listOf(MuscleTarget(MuscleGroup.BACK, backWeight)),
        isWarmup = isWarmup,
        reps = reps
    )
}
