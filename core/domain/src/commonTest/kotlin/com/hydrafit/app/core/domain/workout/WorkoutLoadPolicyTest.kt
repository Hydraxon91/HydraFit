package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutLoadPolicyTest {

    @Test
    fun automaticLoadIsExternalOnly() {
        assertTrue(WorkoutLoadPolicy.allowsAutomaticLoad(ExerciseLoadCapability.EXTERNAL))
        assertFalse(
            WorkoutLoadPolicy.allowsAutomaticLoad(ExerciseLoadCapability.BODYWEIGHT_ONLY)
        )
        assertFalse(
            WorkoutLoadPolicy.allowsAutomaticLoad(ExerciseLoadCapability.BODYWEIGHT_ADDABLE)
        )
        assertFalse(WorkoutLoadPolicy.allowsAutomaticLoad(ExerciseLoadCapability.UNSPECIFIED))
    }

    @Test
    fun defaultKindFollowsCapability() {
        assertEquals(
            LoadKind.EXTERNAL,
            WorkoutLoadPolicy.defaultKind(ExerciseLoadCapability.EXTERNAL)
        )
        assertEquals(
            LoadKind.BODYWEIGHT,
            WorkoutLoadPolicy.defaultKind(ExerciseLoadCapability.BODYWEIGHT_ONLY)
        )
        assertEquals(
            LoadKind.BODYWEIGHT,
            WorkoutLoadPolicy.defaultKind(ExerciseLoadCapability.BODYWEIGHT_ADDABLE)
        )
        assertEquals(
            LoadKind.LEGACY_UNSPECIFIED,
            WorkoutLoadPolicy.defaultKind(ExerciseLoadCapability.UNSPECIFIED)
        )
    }

    @Test
    fun bodyweightOnlyRejectsExternalAndAddedLoad() {
        assertFalse(
            WorkoutLoadPolicy.permits(ExerciseLoadCapability.BODYWEIGHT_ONLY, LoadKind.EXTERNAL)
        )
        assertFalse(
            WorkoutLoadPolicy.permits(ExerciseLoadCapability.BODYWEIGHT_ONLY, LoadKind.ADDED)
        )
        assertTrue(
            WorkoutLoadPolicy.permits(ExerciseLoadCapability.BODYWEIGHT_ONLY, LoadKind.BODYWEIGHT)
        )
    }

    @Test
    fun addablePermitsAddedButNotExternal() {
        assertTrue(
            WorkoutLoadPolicy.permits(ExerciseLoadCapability.BODYWEIGHT_ADDABLE, LoadKind.ADDED)
        )
        assertTrue(
            WorkoutLoadPolicy.permits(
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                LoadKind.BODYWEIGHT
            )
        )
        assertFalse(
            WorkoutLoadPolicy.permits(
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                LoadKind.EXTERNAL
            )
        )
    }

    @Test
    fun validateRejectsANumericBodyweightLoad() {
        assertFailsWith<WorkoutLoadException> {
            WorkoutLoadPolicy.validate(
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                LoadKind.BODYWEIGHT,
                10.0
            )
        }
    }

    @Test
    fun validateRejectsAnExternalLoadOnABodyweightOnlyExercise() {
        assertFailsWith<WorkoutLoadException> {
            WorkoutLoadPolicy.validate(
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                LoadKind.EXTERNAL,
                null
            )
        }
    }

    @Test
    fun validateShapeAllowsNullZeroAndPositiveExternal() {
        WorkoutLoadPolicy.validateShape(LoadKind.EXTERNAL, null)
        WorkoutLoadPolicy.validateShape(LoadKind.EXTERNAL, 0.0)
        WorkoutLoadPolicy.validateShape(LoadKind.EXTERNAL, 32.5)
        assertFailsWith<WorkoutLoadException> {
            WorkoutLoadPolicy.validateShape(LoadKind.EXTERNAL, -1.0)
        }
    }

    @Test
    fun legacyContributesOnlyWhenExternalToday() {
        assertTrue(
            WorkoutLoadPolicy.contributesToLoadMath(
                ExerciseLoadCapability.EXTERNAL,
                LoadKind.LEGACY_UNSPECIFIED
            )
        )
        assertFalse(
            WorkoutLoadPolicy.contributesToLoadMath(
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                LoadKind.LEGACY_UNSPECIFIED
            )
        )
        assertFalse(
            WorkoutLoadPolicy.contributesToLoadMath(
                ExerciseLoadCapability.EXTERNAL,
                LoadKind.ADDED
            )
        )
    }

    @Test
    fun reconcileFallsBackToTheDefaultKindOnIncompatibleLoad() {
        assertEquals(
            LoadKind.BODYWEIGHT to null,
            WorkoutLoadPolicy.reconcile(
                ExerciseLoadCapability.BODYWEIGHT_ONLY,
                LoadKind.EXTERNAL,
                20.0
            )
        )
        assertEquals(
            LoadKind.EXTERNAL to 80.0,
            WorkoutLoadPolicy.reconcile(
                ExerciseLoadCapability.EXTERNAL,
                LoadKind.EXTERNAL,
                80.0
            )
        )
    }
}
