package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeReasonAttributionTest {
    private val curl = Exercise(
        id = "curl",
        name = "Curl",
        requiredEquipment = emptySet(),
        primaryMuscles = setOf(MuscleGroup.BICEPS),
        movementPattern = MovementPattern.BICEPS_ISOLATION
    )

    @Test
    fun onlyRecordedDeterministicSkipsCanClaimASelectionReason() {
        fun assessment(attribution: PlanAttribution, skipped: Set<String>) =
            DirectArmCoverage.assess(
                days = emptyList(),
                exercisesById = mapOf(curl.id to curl),
                compatibleCandidatesByMuscle = mapOf(MuscleGroup.BICEPS to listOf(curl)),
                fatigue = mapOf(MuscleGroup.BICEPS to 0.9),
                isDeload = false,
                attribution = attribution,
                skippedCandidateIds = skipped
            ).single { it.muscle == MuscleGroup.BICEPS }.unmetReason

        assertEquals(
            ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE,
            assessment(PlanAttribution.DETERMINISTIC, setOf("curl"))
        )
        assertEquals(
            ArmCoverageUnmetReason.COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD,
            assessment(PlanAttribution.DETERMINISTIC, emptySet())
        )
        assertEquals(
            ArmCoverageUnmetReason.COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD,
            assessment(PlanAttribution.AI_GENERATED, setOf("curl"))
        )
    }
}
