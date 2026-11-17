package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.SplitType
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeightHistoryEntry
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString

class PlannerPromptBuilderTest {

    private val builder = PlannerPromptBuilder()
    private val ids = listOf("bench-press", "barbell-row")

    @Test
    fun offersCatalogIdsFocusScheduleAndSetCounts() {
        val prompt = builder.build(request(setsPerExercise = 5, accessorySetsPerExercise = 2), ids)

        assertTrue(prompt.text.contains("bench-press"), prompt.text)
        assertTrue(prompt.text.contains("Day 1: "), prompt.text)
        assertTrue(prompt.text.contains("5 sets for compound lifts"), prompt.text)
        assertTrue(prompt.text.contains("2 sets for accessory exercises"), prompt.text)
    }

    @Test
    fun followsTheFocusScheduleAndAllowsARepeatedFocus() {
        val prompt = builder.build(request(daysPerWeek = 5), ids)

        assertTrue(prompt.text.contains("Day 1: UPPER"), prompt.text)
        assertTrue(prompt.text.contains("Day 5: LEGS"), prompt.text)
        assertTrue(prompt.text.contains("a focus may appear on more than one day"), prompt.text)
        assertFalse(prompt.text.contains("different focus"), prompt.text)
    }

    @Test
    fun sendsGoalSpecificRepTargets() {
        val prompt = builder.build(request(goal = TrainingGoal.ENDURANCE), ids)

        assertTrue(prompt.text.contains("Training goal: ENDURANCE"), prompt.text)
        assertTrue(prompt.text.contains("15 reps for compound exercises"), prompt.text)
        assertTrue(prompt.text.contains("15 reps for accessory exercises"), prompt.text)
        assertTrue(prompt.text.contains("about 4 sets per muscle across the week"), prompt.text)
    }

    @Test
    fun mentionsTheDeloadWeek() {
        assertTrue(builder.build(request(isDeload = true), ids).text.contains("deload week"))
    }

    @Test
    fun sendsEquipmentWeightCapsAndPeriodization() {
        val prompt = builder.build(
            request(
                equipmentMaxWeights = mapOf(EquipmentTag.BARBELL to 200.0),
                weekNumber = 4,
                cycleNumber = 2
            ),
            ids
        )

        assertTrue(prompt.text.contains("Equipment weight limits (do not exceed)"), prompt.text)
        assertTrue(prompt.text.contains("Barbell: 200.0kg"), prompt.text)
        assertTrue(prompt.text.contains("Periodization: week 4 of cycle 2."), prompt.text)
    }

    @Test
    fun sendsRecentAcceptedSelectionsToSteerRotation() {
        val prompt = builder.build(
            request(
                recentExerciseIdsByPattern = mapOf(
                    MovementPattern.HORIZONTAL_PUSH to setOf("bench-press")
                )
            ),
            ids
        )

        assertTrue(prompt.text.contains("previous accepted week"), prompt.text)
        assertTrue(prompt.text.contains("HORIZONTAL_PUSH: bench-press"), prompt.text)
    }

    @Test
    fun boundsArrayLengthsInTheSchema() {
        val json = plannerJson.encodeToString(builder.build(request(daysPerWeek = 3), ids).schema)

        assertTrue(json.contains("\"minItems\":3"), "days should be bounded to 3")
        assertTrue(json.contains("\"maxItems\":3"), "days should be bounded to 3")
        assertTrue(json.contains("\"minItems\":4"), "exercises per day should have a floor")
        assertTrue(json.contains("\"maxItems\":6"), "exercises per day should have a ceiling")
    }

    @Test
    fun omitsWorkoutDataUnlessSharingIsEnabled() {
        val prompt = builder.build(request(), ids)

        assertFalse(prompt.text.contains("Recent working weights"), prompt.text)
        assertFalse(prompt.text.contains("suggestedWeightKg"), prompt.text)
        assertFalse(plannerJson.encodeToString(prompt.schema).contains("suggestedWeightKg"))
    }

    @Test
    fun sendsRecentWeightsWhenSharingIsEnabled() {
        val prompt = builder.build(
            request(
                includeWorkoutData = true,
                recentWeights = listOf(WeightHistoryEntry("bench-press", 0L, 100.0, 5))
            ),
            ids
        )

        assertTrue(prompt.text.contains("Recent working weights"), prompt.text)
        assertTrue(prompt.text.contains("suggestedWeightKg"), prompt.text)
        assertTrue(plannerJson.encodeToString(prompt.schema).contains("suggestedWeightKg"))
    }

    @Test
    fun labelsProgressedValuesAsOneRepMaximumsOnlyWhenSharingIsEnabled() {
        val off = builder.build(request(suggestedWeightsKg = mapOf("bench-press" to 90.0)), ids)
        assertFalse(off.text.contains("Estimated one-rep maximums"), off.text)

        val on = builder.build(
            request(
                includeWorkoutData = true,
                suggestedWeightsKg = mapOf("bench-press" to 90.0)
            ),
            ids
        )
        assertTrue(on.text.contains("Estimated one-rep maximums"), on.text)
        assertTrue(on.text.contains("bench-press: 90.0kg"), on.text)
        assertTrue(on.text.contains("do not copy them into"), on.text)
        assertEquals(1, Regex("Estimated one-rep maximums").findAll(on.text).count())
    }

    private fun request(
        daysPerWeek: Int = 3,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets,
        accessorySetsPerExercise: Int = goal.accessorySets,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        weekNumber: Int = 1,
        cycleNumber: Int = 1,
        includeWorkoutData: Boolean = false,
        isDeload: Boolean = false,
        recentWeights: List<WeightHistoryEntry> = emptyList()
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        splitPreference = SplitType.AUTO,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        recentExerciseIdsByPattern = recentExerciseIdsByPattern,
        suggestedWeightsKg = suggestedWeightsKg,
        equipmentMaxWeights = equipmentMaxWeights,
        weekNumber = weekNumber,
        cycleNumber = cycleNumber,
        includeWorkoutData = includeWorkoutData,
        isDeload = isDeload,
        recentWeights = recentWeights
    )
}
