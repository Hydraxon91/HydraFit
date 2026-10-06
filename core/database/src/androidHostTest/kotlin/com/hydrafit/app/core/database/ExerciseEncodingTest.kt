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

    @Test
    fun dropsIndividuallyMalformedTokensWithoutKeepingAnEntry() {
        // Missing separator, extra separator, non-numeric weight, unknown name, empty weight.
        assertEquals(emptyMap(), decodeInvolvements("LATS"))
        assertEquals(emptyMap(), decodeInvolvements("LATS:1.0:2.0"))
        assertEquals(emptyMap(), decodeInvolvements("LATS:abc"))
        assertEquals(emptyMap(), decodeInvolvements("NOT_A_MUSCLE:1.0"))
        assertEquals(emptyMap(), decodeInvolvements("LATS:"))
    }

    @Test
    fun keepsValidTokensAlongsideMalformedOnes() {
        assertEquals(
            mapOf(MuscleGroup.LATS to 1.0, MuscleGroup.TRICEPS to 0.4),
            decodeInvolvements("LATS:1.0,BICEPS:bad,TRICEPS:0.4")
        )
    }

    @Test
    fun aMalformedTokenDoesNotDiscardValidLegacyExpansion() {
        assertEquals(
            mapOf(
                MuscleGroup.ABS to 0.35,
                MuscleGroup.OBLIQUES to 0.15,
                MuscleGroup.BICEPS to 1.0
            ),
            decodeInvolvements("CORE:0.5,NOT_A_MUSCLE:1.0,BICEPS:1.0")
        )
    }

    @Test
    fun roundTripsTheExtendedMuscleGroups() {
        val involvements = mapOf(
            MuscleGroup.ADDUCTORS to 1.0,
            MuscleGroup.HIP_ABDUCTORS to 0.5,
            MuscleGroup.TRAPS to 0.5,
            MuscleGroup.NECK to 0.3
        )

        assertEquals(involvements, decodeInvolvements(encodeInvolvements(involvements)))
    }

    @Test
    fun sumsDuplicateContributionsForTheSameMuscle() {
        assertEquals(mapOf(MuscleGroup.LATS to 0.5), decodeInvolvements("LATS:0.3,LATS:0.2"))
        assertEquals(
            mapOf(
                MuscleGroup.LATS to 1.0,
                MuscleGroup.UPPER_BACK to 0.35,
                MuscleGroup.LOWER_BACK to 0.15
            ),
            decodeInvolvements("BACK:1.0,LATS:0.5")
        )
    }
}
