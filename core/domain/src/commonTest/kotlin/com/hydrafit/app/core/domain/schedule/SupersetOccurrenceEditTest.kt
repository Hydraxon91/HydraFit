package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SupersetOccurrenceEditTest {
    private val pair = Superset("arms", 10, 120)

    private fun entry(position: Int, exerciseId: String, superset: Superset? = null) =
        OccurrenceEntry(
            position = position,
            exerciseId = exerciseId,
            exerciseName = exerciseId,
            movementPattern = MovementPattern.BICEPS_ISOLATION,
            sets = 3,
            reps = 10,
            loadKind = LoadKind.EXTERNAL,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            superset = superset
        )

    @Test
    fun unchangedAdjacentMembersKeepTheirPair() {
        val existing = listOf(entry(0, "curl", pair), entry(1, "pushdown", pair), entry(2, "row"))
        val rebuilt = listOf(entry(0, "curl"), entry(1, "pushdown"), entry(2, "row"))

        val result = preserveUnchangedSupersets(existing, rebuilt)

        assertEquals(pair, result[0].superset)
        assertEquals(pair, result[1].superset)
        assertNull(result[2].superset)
    }

    @Test
    fun replacingEitherMemberDropsBothSidesInsteadOfLeavingAHalfPair() {
        val existing = listOf(entry(0, "curl", pair), entry(1, "pushdown", pair))
        val rebuilt = listOf(entry(0, "curl"), entry(1, "dip"))

        val result = preserveUnchangedSupersets(existing, rebuilt)

        assertNull(result[0].superset)
        assertNull(result[1].superset)
    }

    @Test
    fun aMoveThatSeparatesTheMembersDropsThePair() {
        val existing = listOf(entry(0, "curl", pair), entry(1, "pushdown", pair), entry(2, "row"))
        val rebuilt = listOf(entry(0, "curl"), entry(1, "row"), entry(2, "pushdown"))

        val result = preserveUnchangedSupersets(existing, rebuilt)

        assertNull(result[0].superset)
        assertNull(result[2].superset)
    }

    @Test
    fun ambiguousDuplicateMembershipKeepsNoPairs() {
        val existing = listOf(entry(0, "curl", pair), entry(1, "pushdown", pair))
        val rebuilt = listOf(entry(0, "curl"), entry(1, "curl"))

        assertNull(preserveUnchangedSupersets(existing, rebuilt)[0].superset)
    }
}
