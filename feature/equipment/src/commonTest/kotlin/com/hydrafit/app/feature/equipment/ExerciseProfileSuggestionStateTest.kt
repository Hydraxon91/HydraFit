package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExerciseProfile
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseProfileSuggestionStateTest {
    private val candidate = CatalogExerciseProfile(
        catalogId = "seed-id",
        canonicalName = "Catalog name",
        displayName = "Renamed catalog",
        profile = ExerciseProfile(
            setOf(EquipmentTag.DUMBBELL),
            MovementPattern.HORIZONTAL_PUSH,
            mapOf(MuscleGroup.CHEST_UPPER to 0.83, MuscleGroup.TRICEPS to 0.17),
            ExerciseLoadCapability.UNSPECIFIED,
            true
        )
    )

    @Test
    fun everySelectionCombinationAppliesOnlySelectedGroupsAndProtectsThem() {
        val initial = ExerciseEditorState(
            isNew = true,
            isCustom = true,
            name = "  My own name  ",
            equipment = setOf(EquipmentTag.BARBELL),
            involvements = mapOf(MuscleGroup.ABS to 1.0)
        )
        val groups = ExerciseProfileGroup.entries
        repeat(1 shl groups.size) { mask ->
            val selected = groups.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
            val preview = initial.previewProfile(candidate)
            val checked = preview.copy(
                suggestion = preview.suggestion.copy(selectedGroups = selected)
            )
            val applied = checked.applySelectedProfile()
            if (selected.isEmpty()) {
                assertEquals(checked, applied)
            } else {
                assertEquals(initial.name, applied.name)
                assertEquals(initial.exerciseId, applied.exerciseId)
                assertEquals(selected, applied.touchedGroups)
                assertEquals(ExerciseProfileSuggestionState(), applied.suggestion)
                assertEquals(
                    if (ExerciseProfileGroup.EQUIPMENT in selected) {
                        candidate.profile.equipment
                    } else {
                        initial.equipment
                    },
                    applied.equipment
                )
                assertEquals(
                    if (ExerciseProfileGroup.PATTERN in selected) {
                        candidate.profile.movementPattern
                    } else {
                        initial.movementPattern
                    },
                    applied.movementPattern
                )
                assertEquals(
                    if (ExerciseProfileGroup.INVOLVEMENTS in selected) {
                        candidate.profile.involvements
                    } else {
                        initial.involvements
                    },
                    applied.involvements
                )
                assertEquals(
                    if (ExerciseProfileGroup.LOAD in selected) {
                        candidate.profile.loadCapability
                    } else {
                        initial.loadCapability
                    },
                    applied.loadCapability
                )
                assertEquals(
                    if (ExerciseProfileGroup.UNILATERAL in selected) {
                        candidate.profile.isUnilateral
                    } else {
                        initial.isUnilateral
                    },
                    applied.isUnilateral
                )
                assertEquals(
                    groups.toSet() - selected,
                    applied.previewProfile(candidate).suggestion.selectedGroups
                )
            }
        }
    }

    @Test
    fun wholeInvolvementMapReplacementAndEmptyProposalKeepSaveValidationAdvisory() {
        val initial = ExerciseEditorState(
            isNew = true,
            isCustom = true,
            name = "Custom",
            involvements = mapOf(MuscleGroup.ABS to 1.0)
        )
        val applied = initial.previewProfile(candidate).applySelectedProfile()
        assertEquals(candidate.profile.involvements, applied.involvements)
        assertFalse(MuscleGroup.ABS in applied.involvements)
        assertTrue(applied.canSave)
        val empty = candidate.copy(profile = candidate.profile.copy(involvements = emptyMap()))
        val emptyApplied = initial.previewProfile(empty).applySelectedProfile()
        assertTrue(emptyApplied.involvements.isEmpty())
        assertFalse(emptyApplied.canSave)
        val mismatch = candidate.copy(
            profile = candidate.profile.copy(movementPattern = MovementPattern.SQUAT)
        )
        val mismatched = initial.previewProfile(mismatch).applySelectedProfile()
        assertTrue(mismatched.patternMismatch)
        assertTrue(mismatched.canSave)
    }

    @Test
    fun existingEditorsCannotApplyProfile() {
        listOf(
            ExerciseEditorState(exerciseId = "existing-custom", isCustom = true),
            ExerciseEditorState(exerciseId = "existing-built-in")
        ).forEach {
            val preview = it.previewProfile(candidate)
            assertEquals(preview, preview.applySelectedProfile())
        }
    }
}
