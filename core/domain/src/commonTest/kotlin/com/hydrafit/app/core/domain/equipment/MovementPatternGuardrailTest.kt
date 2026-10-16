package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovementPatternGuardrailTest {

    @Test
    fun acceptsNewLegIsolationMusclesWithoutAcceptingUnrelatedPatternsOrMuscles() {
        listOf(MuscleGroup.TIBIALIS_ANTERIOR, MuscleGroup.HIP_FLEXORS).forEach { muscle ->
            val profile = mapOf(muscle to 1.0)
            assertFalse(MovementPatternGuardrail.conflicts(MovementPattern.LEG_ISOLATION, profile))
            assertTrue(MovementPatternGuardrail.conflicts(MovementPattern.HORIZONTAL_PUSH, profile))
        }
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.LEG_ISOLATION,
                mapOf(MuscleGroup.BICEPS to 1.0)
            )
        )
    }

    @Test
    fun manualIsolationPatternsMatchOnlyTheirOwnMuscles() {
        val profiles = mapOf(
            MovementPattern.TRAP_ISOLATION to MuscleGroup.TRAPS,
            MovementPattern.NECK_ISOLATION to MuscleGroup.NECK
        )
        profiles.forEach { (pattern, muscle) ->
            assertFalse(pattern.isCompound)
            assertEquals(setOf(muscle), MovementPatternGuardrail.expectedMusclesFor(pattern))
            assertFalse(MovementPatternGuardrail.conflicts(pattern, mapOf(muscle to 1.0)))
            assertTrue(
                MovementPatternGuardrail.conflicts(pattern, mapOf(MuscleGroup.SIDE_DELTS to 1.0))
            )
        }
    }

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
                mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.TRICEPS to 0.4)
            )
        )
    }

    @Test
    fun acceptsAShoulderDominantUprightRowAsAVerticalPull() {
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.VERTICAL_PULL,
                mapOf(MuscleGroup.SIDE_DELTS to 1.0, MuscleGroup.LATS to 0.3)
            )
        )
    }

    @Test
    fun acceptsUprightTrapsAndHipAdductionWithoutChangingTheThreshold() {
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.VERTICAL_PULL,
                mapOf(MuscleGroup.TRAPS to 0.7)
            )
        )
        assertFalse(
            MovementPatternGuardrail.conflicts(
                MovementPattern.LEG_ISOLATION,
                mapOf(MuscleGroup.ADDUCTORS to 1.0)
            )
        )
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.VERTICAL_PULL,
                mapOf(MuscleGroup.TRAPS to 0.5)
            )
        )
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.VERTICAL_PULL,
                mapOf(MuscleGroup.QUADS to 1.0)
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
                mapOf(MuscleGroup.CHEST_UPPER to MovementPatternGuardrail.PRIMARY_THRESHOLD)
            )
        )
        assertTrue(
            MovementPatternGuardrail.conflicts(
                MovementPattern.HORIZONTAL_PUSH,
                mapOf(MuscleGroup.CHEST_UPPER to MovementPatternGuardrail.PRIMARY_THRESHOLD - 0.01)
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
            setOf(
                MuscleGroup.LATS,
                MuscleGroup.UPPER_BACK,
                MuscleGroup.REAR_DELTS,
                MuscleGroup.BICEPS
            ),
            MovementPatternGuardrail.expectedMusclesFor(MovementPattern.HORIZONTAL_PULL)
        )
    }
}
