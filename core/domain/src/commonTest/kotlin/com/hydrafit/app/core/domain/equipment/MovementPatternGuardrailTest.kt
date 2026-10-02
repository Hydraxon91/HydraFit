package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementPatternGuardrailTest {

    @Test
    fun flagsAProfileThatDoesNotLoadThePatternsMuscles() {
        // Leg extension filed as a horizontal press.
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(MuscleGroup.QUADS to 1.0)
            )
        )
    }

    @Test
    fun acceptsACoherentProfile() {
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.TRICEPS to 0.4)
            )
        )
    }

    @Test
    fun flagsAShoulderDominantExerciseFiledAsAVerticalPull() {
        // Upright row filed as a pull: no back/biceps primary.
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.VERTICAL_PULL,
                mapOf(MuscleGroup.SHOULDERS to 1.0, MuscleGroup.BACK to 0.3)
            )
        )
    }

    @Test
    fun doesNotFlagACurlFiledAsAPullBecauseBicepsMatchesThePattern() {
        // The muscle rule cannot catch a pattern-type mistake (isolation vs compound); Q4e fixes
        // this in the user catalog. Documented so the limitation is explicit.
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PULL,
                mapOf(MuscleGroup.BICEPS to 1.0)
            )
        )
    }

    @Test
    fun usesThePrimaryThresholdForTheBoundary() {
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(MuscleGroup.CHEST to MovementPatternGuardrail.PRIMARY_THRESHOLD)
            )
        )
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(MuscleGroup.CHEST to MovementPatternGuardrail.PRIMARY_THRESHOLD - 0.01)
            )
        )
    }

    @Test
    fun anEmptyProfileIsNotFlagged() {
        assertFalse(MovementPatternGuardrail.conflicts(MovementPattern.CORE, emptyMap()))
    }

    @Test
    fun expectsTheRightMusclesPerPattern() {
        assertEquals(
            setOf(MuscleGroup.CALVES),
            MovementPatternGuardrail.expectedMusclesFor(MovementPattern.CALF_RAISE)
        )
        assertEquals(
            setOf(MuscleGroup.BACK, MuscleGroup.BICEPS),
            MovementPatternGuardrail.expectedMusclesFor(MovementPattern.HORIZONTAL_PULL)
        )
    }
}
