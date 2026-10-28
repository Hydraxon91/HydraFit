package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargetOverride
import com.hydrafit.app.core.domain.schedule.GuidedWorkoutTargets
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GuidedWorkoutTargetsBackupTest {
    @Test
    fun occurrencePayloadRoundTripsAndRejectsWrongEntryShape() {
        val entry = OccurrenceEntry(
            id = 1L,
            position = 0,
            exerciseId = "squat",
            exerciseName = "Squat",
            movementPattern = MovementPattern.SQUAT,
            sets = 2,
            reps = 5,
            weightKg = 80.0,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            loadKind = LoadKind.EXTERNAL
        )
        val targets = GuidedWorkoutTargets(
            overrides = listOf(
                GuidedWorkoutTargetOverride(1, SetTarget(reps = 8, weightKg = 0.0))
            )
        )
        val encoded = GuidedWorkoutTargets.encode(targets, entry)
        assertEquals(targets, GuidedWorkoutTargets.decode(encoded, entry))
        assertFailsWith<IllegalArgumentException> {
            GuidedWorkoutTargets.decode(encoded, entry.copy(sets = 1))
        }
        assertFailsWith<IllegalArgumentException> {
            GuidedWorkoutTargets.decode("{\"version\":2,\"overrides\":[]}", entry)
        }
    }
}
