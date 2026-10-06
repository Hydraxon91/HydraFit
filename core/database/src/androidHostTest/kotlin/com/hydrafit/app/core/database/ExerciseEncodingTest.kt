package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseEncodingTest {

    @Test
    fun expandsLegacyChestIntoUpperAndLower() {
        assertEquals(
            mapOf(MuscleGroup.CHEST_UPPER to 0.5, MuscleGroup.CHEST_LOWER to 0.5),
            decodeInvolvements("CHEST:1.0")
        )
    }

    @Test
    fun expandsLegacyBackIntoThreeRegions() {
        assertEquals(
            mapOf(
                MuscleGroup.LATS to 0.5,
                MuscleGroup.UPPER_BACK to 0.35,
                MuscleGroup.LOWER_BACK to 0.15
            ),
            decodeInvolvements("BACK:1.0")
        )
    }

    @Test
    fun expandsLegacyShouldersAndCore() {
        assertEquals(
            mapOf(
                MuscleGroup.FRONT_DELTS to 0.3,
                MuscleGroup.SIDE_DELTS to 0.4,
                MuscleGroup.REAR_DELTS to 0.3
            ),
            decodeInvolvements("SHOULDERS:1.0")
        )
        assertEquals(
            mapOf(MuscleGroup.ABS to 0.7, MuscleGroup.OBLIQUES to 0.3),
            decodeInvolvements("CORE:1.0")
        )
    }

    @Test
    fun decodesCurrentNamesAndMixesWithLegacy() {
        assertEquals(
            mapOf(
                MuscleGroup.BICEPS to 1.0,
                MuscleGroup.CHEST_UPPER to 0.5,
                MuscleGroup.CHEST_LOWER to 0.5
            ),
            decodeInvolvements("BICEPS:1.0,CHEST:1.0")
        )
        assertEquals(emptyMap(), decodeInvolvements(null))
        assertEquals(emptyMap(), decodeInvolvements(""))
    }
}
