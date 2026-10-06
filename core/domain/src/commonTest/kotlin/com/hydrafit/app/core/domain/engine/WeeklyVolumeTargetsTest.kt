package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyVolumeTargetsTest {

    @Test
    fun everyGoalHasAnOrderedWindowWithinSaneBounds() {
        TrainingGoal.entries.forEach { goal ->
            val target = WeeklyVolumeTargets.forGoal(goal)
            assertTrue(
                target.minSets <= target.targetSets && target.targetSets <= target.maxSets,
                "goal=$goal -> $target"
            )
            assertTrue(target.minSets > 0.0, "goal=$goal")
        }
    }

    @Test
    fun hypertrophyTargetsTheMostVolumeAndStrengthTheLeast() {
        val hypertrophy = WeeklyVolumeTargets.forGoal(TrainingGoal.HYPERTROPHY)
        val balanced = WeeklyVolumeTargets.forGoal(TrainingGoal.BALANCED)
        val strength = WeeklyVolumeTargets.forGoal(TrainingGoal.STRENGTH)

        assertTrue(hypertrophy.targetSets > balanced.targetSets)
        assertTrue(balanced.targetSets > strength.targetSets)
    }

    @Test
    fun weightedSetsSumSetsTimesInvolvementAcrossDays() {
        val bench = exercise(
            "bench",
            mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.TRICEPS to 0.4)
        )
        val row = exercise("row", mapOf(MuscleGroup.LATS to 1.0, MuscleGroup.BICEPS to 0.3))
        val days = listOf(
            day(0, listOf("bench" to 3, "row" to 4)),
            day(1, listOf("bench" to 2))
        )

        val exercisesById = mapOf(bench.id to bench, row.id to row)
        val volume = WeeklyVolumeTargets.weightedSetsByMuscle(days, exercisesById)

        assertEquals(5.0, volume.getValue(MuscleGroup.CHEST_UPPER))
        assertEquals(4.0, volume.getValue(MuscleGroup.LATS))
        assertEquals(2.0, volume.getValue(MuscleGroup.TRICEPS))
        assertEquals(1.2, volume.getValue(MuscleGroup.BICEPS), 1e-9)
    }

    @Test
    fun untrainedMusclesAreReportedAtZero() {
        val bench = exercise("bench", mapOf(MuscleGroup.CHEST_UPPER to 1.0))

        val volume = WeeklyVolumeTargets.weightedSetsByMuscle(
            listOf(day(0, listOf("bench" to 3))),
            mapOf(bench.id to bench)
        )

        assertEquals(MuscleGroup.entries.size, volume.size)
        assertEquals(0.0, volume.getValue(MuscleGroup.CALVES))
    }

    @Test
    fun unknownExerciseIdsAreIgnored() {
        val volume = WeeklyVolumeTargets.weightedSetsByMuscle(
            listOf(day(0, listOf("ghost" to 5))),
            emptyMap()
        )

        assertTrue(volume.values.all { it == 0.0 })
    }

    private fun day(index: Int, exercises: List<Pair<String, Int>>) = WorkoutDay(
        dayIndex = index,
        focus = SplitFocus.FULL_BODY,
        exercises = exercises.map { (id, sets) ->
            PlannedExercise(exerciseId = id, sets = sets, reps = 8)
        }
    )

    private fun exercise(id: String, involvements: Map<MuscleGroup, Double>) = Exercise(
        id = id,
        name = id,
        requiredEquipment = emptySet<EquipmentTag>(),
        primaryMuscles = involvements.keys,
        movementPattern = MovementPattern.HORIZONTAL_PUSH,
        involvements = involvements
    )
}
