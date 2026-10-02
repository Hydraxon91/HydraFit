package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlannerPromptFragmentsTest {

    @Test
    fun formatsTheEquipmentLine() {
        val request = request(
            availableEquipment = setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH)
        )
        assertEquals(
            "Available equipment: Barbell, Bench",
            PlannerPromptFragments.equipmentLine(request)
        )
    }

    @Test
    fun formatsTheFatigueLineWithTheGivenLabel() {
        val request = request(
            muscleFatigue = mapOf(MuscleGroup.CHEST to 0.25, MuscleGroup.BACK to 0.5)
        )
        assertEquals(
            "Muscle fatigue (0.0-1.0): CHEST=0.25, BACK=0.5",
            PlannerPromptFragments.fatigueLine(request, "Muscle fatigue")
        )
        assertEquals(
            "Current muscle fatigue (0.0-1.0): CHEST=0.25, BACK=0.5",
            PlannerPromptFragments.fatigueLine(request, "Current muscle fatigue")
        )
    }

    @Test
    fun deloadInstructionIsAbsentUnlessDeload() {
        assertNull(PlannerPromptFragments.deloadInstruction(request(isDeload = false)))
        val text = assertNotNull(PlannerPromptFragments.deloadInstruction(request(isDeload = true)))
        assertTrue(text.contains("deload week"), text)
    }

    @Test
    fun derivesVolumeGuidanceFromTheGoal() {
        assertEquals(
            "Scale reps to keep volume steady: fewer sets mean more reps per set. Aim for " +
                "about 18 total reps for compound lifts and 24 for accessory exercises.",
            PlannerPromptFragments.volumeRepsGuidance(request(goal = TrainingGoal.BALANCED))
        )
    }

    @Test
    fun formatsRecentWeightsWhenSharingIsOn() {
        val request = request(
            includeWorkoutData = true,
            recentWeights = listOf(WeightHistoryEntry("squat", 0L, 100.0, 5))
        )
        assertEquals(
            "squat 1970-01-01: 100.0kg x 5",
            PlannerPromptFragments.recentWeightsList(request)
        )
    }

    @Test
    fun recentWeightsAreAbsentWhenSharingIsOffOrEmpty() {
        assertNull(PlannerPromptFragments.recentWeightsList(request(includeWorkoutData = false)))
        assertNull(PlannerPromptFragments.recentWeightsList(request(includeWorkoutData = true)))
    }

    @Test
    fun formatsProgressedWeightsSortedWhenSharingIsOn() {
        val request = request(
            includeWorkoutData = true,
            suggestedWeightsKg = mapOf("squat" to 102.5, "bench" to 80.0)
        )
        assertEquals(
            "bench: 80.0kg; squat: 102.5kg",
            PlannerPromptFragments.progressedWeightsList(request)
        )
    }

    @Test
    fun progressedWeightsAreAbsentWhenSharingIsOffOrEmpty() {
        assertNull(
            PlannerPromptFragments.progressedWeightsList(request(includeWorkoutData = false))
        )
        assertNull(PlannerPromptFragments.progressedWeightsList(request(includeWorkoutData = true)))
    }

    private fun request(
        availableEquipment: Set<EquipmentTag> = emptySet(),
        muscleFatigue: Map<MuscleGroup, Double> = emptyMap(),
        goal: TrainingGoal = TrainingGoal.BALANCED,
        includeWorkoutData: Boolean = false,
        recentWeights: List<WeightHistoryEntry> = emptyList(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        isDeload: Boolean = false
    ) = PlanRequest(
        daysPerWeek = 3,
        availableEquipment = availableEquipment,
        muscleFatigue = muscleFatigue,
        nowMillis = 0L,
        goal = goal,
        includeWorkoutData = includeWorkoutData,
        recentWeights = recentWeights,
        suggestedWeightsKg = suggestedWeightsKg,
        isDeload = isDeload
    )
}
