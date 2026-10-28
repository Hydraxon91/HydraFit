package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GuidedWorkoutTargetsTest {
    @Test
    fun appliesOnlyMaskedFieldsToRemainingOrderedTargetsAndKeepsOriginal() {
        val original = entry(
            setTargets = SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 6, weightKg = 80.0, targetRir = 2),
                    SetTarget(reps = 7, weightKg = 82.5, targetRir = 1),
                    SetTarget(reps = 8, weightKg = 85.0, targetRir = 0)
                )
            )
        )
        val edits = applyGuidedTargetPatch(
            original,
            null,
            1,
            false,
            mapOf(
                1 to GuidedTargetPatch(
                    SetTarget(reps = 9, weightKg = 70.0),
                    setOf(GuidedTargetField.REPS, GuidedTargetField.LOAD)
                )
            )
        )!!
        assertEquals(
            SetTarget(reps = 6, weightKg = 80.0, targetRir = 2),
            edits.targetFor(original, 0).target
        )
        assertEquals(
            SetTarget(reps = 9, weightKg = 70.0, targetRir = 1),
            edits.targetFor(original, 1).target
        )
        assertEquals(
            SetTarget(reps = 8, weightKg = 85.0, targetRir = 0),
            edits.targetFor(original, 2).target
        )
        assertEquals(7, original.setTargets?.targets?.get(1)?.reps)
    }

    @Test
    fun loadOnlyAndEmptyMasksPreserveEachOrderedTargetAndRejectAbsentMetricEdits() {
        val original = entry(
            setTargets = SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 6, weightKg = 80.0, targetRir = 2),
                    SetTarget(reps = 7, weightKg = 82.5, targetRir = 1),
                    SetTarget(reps = 8, weightKg = 85.0, targetRir = 0)
                )
            )
        )

        val loadOnly = applyGuidedTargetPatch(
            original,
            null,
            1,
            false,
            mapOf(
                1 to
                    GuidedTargetPatch(
                        SetTarget(reps = 5, weightKg = 70.0),
                        setOf(GuidedTargetField.LOAD)
                    )
            )
        )!!
        assertEquals(
            SetTarget(reps = 6, weightKg = 80.0, targetRir = 2),
            loadOnly.targetFor(original, 0).target
        )
        assertEquals(
            SetTarget(reps = 7, weightKg = 70.0, targetRir = 1),
            loadOnly.targetFor(original, 1).target
        )
        assertEquals(
            SetTarget(reps = 8, weightKg = 85.0, targetRir = 0),
            loadOnly.targetFor(original, 2).target
        )

        val emptyMask = applyGuidedTargetPatch(
            original,
            null,
            1,
            false,
            mapOf(2 to GuidedTargetPatch(SetTarget(reps = 5, weightKg = 70.0), emptySet()))
        )
        assertNull(emptyMask)

        assertFailsWith<IllegalArgumentException> {
            applyGuidedTargetPatch(
                entry(reps = 8),
                null,
                0,
                false,
                mapOf(
                    0 to GuidedTargetPatch(
                        SetTarget(durationSeconds = 30),
                        setOf(GuidedTargetField.DURATION_SECONDS)
                    )
                )
            )
        }
    }

    @Test
    fun explicitNullAndZeroLoadsRemainDistinctAndResetKeepsCompletedPrefix() {
        val original = entry(
            setTargets = SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 5, weightKg = 80.0),
                    SetTarget(reps = 5, weightKg = 80.0),
                    SetTarget(reps = 5, weightKg = 80.0)
                )
            )
        )
        val first = applyGuidedTargetPatch(
            original,
            null,
            0,
            false,
            mapOf(
                0 to
                    GuidedTargetPatch(
                        SetTarget(reps = 5, weightKg = null),
                        setOf(GuidedTargetField.LOAD)
                    ),
                1 to
                    GuidedTargetPatch(
                        SetTarget(reps = 5, weightKg = 0.0),
                        setOf(GuidedTargetField.LOAD)
                    )
            )
        )!!
        assertNull(first.targetFor(original, 0).target.weightKg)
        assertEquals(0.0, first.targetFor(original, 1).target.weightKg)
        val reset = applyGuidedTargetPatch(original, first, 1, true, emptyMap())!!
        assertEquals(0, reset.overrides.single().setIndex)
        assertNull(reset.overrides.single().target.weightKg)
        assertEquals(80.0, reset.targetFor(original, 1).target.weightKg)
    }

    @Test
    fun resetAndPatchRestoresRemainingOriginalsThenAppliesEditsAtomically() {
        val original = entry(
            setTargets = SlotSetTargets.of(
                listOf(
                    SetTarget(reps = 5, weightKg = 80.0),
                    SetTarget(reps = 6, weightKg = 82.5, targetRir = 2),
                    SetTarget(reps = 7, weightKg = 85.0, targetRir = 1)
                )
            )
        )
        val existing = applyGuidedTargetPatch(
            original,
            null,
            0,
            false,
            mapOf(
                0 to GuidedTargetPatch(
                    SetTarget(reps = 5, weightKg = 90.0),
                    setOf(GuidedTargetField.LOAD)
                ),
                1 to GuidedTargetPatch(
                    SetTarget(reps = 6, weightKg = 92.5),
                    setOf(GuidedTargetField.LOAD)
                )
            )
        )!!

        val composed = applyGuidedTargetPatch(
            original,
            existing,
            1,
            true,
            mapOf(
                2 to GuidedTargetPatch(
                    SetTarget(reps = 8, weightKg = 85.0),
                    setOf(GuidedTargetField.REPS)
                )
            )
        )!!

        assertEquals(90.0, composed.targetFor(original, 0).target.weightKg)
        assertEquals(82.5, composed.targetFor(original, 1).target.weightKg)
        assertEquals(8, composed.targetFor(original, 2).target.reps)
        assertEquals(85.0, composed.targetFor(original, 2).target.weightKg)
        assertEquals(2, composed.overrides.size)
    }

    @Test
    fun preservesClusterRirAndRejectsInvalidDropSetOrderingOrChangedSegmentCount() {
        val cluster = ClusterPrescription(
            ClusterKind.DROP_SET,
            listOf(ClusterSegmentTarget(8, 80.0, 2), ClusterSegmentTarget(6, 70.0, 1))
        )
        val original = entry(reps = 8, weightKg = 80.0, cluster = cluster)
        val edited = applyGuidedTargetPatch(
            original,
            null,
            0,
            false,
            mapOf(
                0 to GuidedTargetPatch(
                    SetTarget(reps = 8, weightKg = 90.0),
                    setOf(GuidedTargetField.LOAD),
                    mapOf(
                        1 to
                            ClusterSegmentPatch(
                                ClusterSegmentTarget(6, 70.0),
                                setOf(ClusterSegmentField.LOAD)
                            )
                    )
                )
            )
        )!!
        assertEquals(2, edited.overrides.single().cluster?.segments?.get(0)?.rir)
        assertEquals(1, edited.overrides.single().cluster?.segments?.get(1)?.rir)
    }

    @Test
    fun rejectsEditingCompletedSetAndMetricShape() {
        val original = entry(reps = 5)
        assertFailsWith<IllegalArgumentException> {
            applyGuidedTargetPatch(
                original,
                null,
                1,
                false,
                mapOf(
                    0 to GuidedTargetPatch(SetTarget(reps = 6), setOf(GuidedTargetField.REPS))
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            applyGuidedTargetPatch(
                original,
                null,
                0,
                false,
                mapOf(
                    0 to
                        GuidedTargetPatch(
                            SetTarget(durationSeconds = 30),
                            setOf(GuidedTargetField.REPS)
                        )
                )
            )
        }
    }

    @Test
    fun resolvingScalarTargetsAndEmptyPatchDoNotInventOverrides() {
        val scalar = entry(sets = 2, reps = 6, weightKg = 50.0)
        assertEquals(6, originalTargets(scalar)[1].target.reps)
        assertNull(applyGuidedTargetPatch(scalar, null, 0, false, emptyMap()))
        assertFailsWith<IllegalArgumentException> {
            applyGuidedTargetPatch(
                scalar,
                null,
                0,
                false,
                mapOf(0 to GuidedTargetPatch(SetTarget(reps = 0), setOf(GuidedTargetField.REPS)))
            )
        }
    }

    @Test
    fun clusterSegmentChangesPreserveUnmaskedValuesAndRejectOrderingViolation() {
        val cluster = ClusterPrescription(
            ClusterKind.DROP_SET,
            listOf(
                ClusterSegmentTarget(8, 80.0, 3),
                ClusterSegmentTarget(6, 70.0, 2),
                ClusterSegmentTarget(4, 60.0, 1)
            )
        )
        val original = entry(sets = 1, reps = 8, weightKg = 80.0, cluster = cluster)
        val patch = applyGuidedTargetPatch(
            original,
            null,
            0,
            false,
            mapOf(
                0 to GuidedTargetPatch(
                    SetTarget(reps = 8, weightKg = 80.0),
                    emptySet(),
                    mapOf(
                        1 to ClusterSegmentPatch(
                            ClusterSegmentTarget(7, 70.0),
                            setOf(ClusterSegmentField.REPS)
                        )
                    )
                )
            )
        )!!
        assertEquals(7, patch.targetFor(original, 0).cluster?.segments?.get(1)?.reps)
        assertEquals(60.0, patch.targetFor(original, 0).cluster?.segments?.get(2)?.weightKg)
        assertFailsWith<IllegalArgumentException> {
            applyGuidedTargetPatch(
                original,
                null,
                0,
                false,
                mapOf(
                    0 to GuidedTargetPatch(
                        SetTarget(reps = 8, weightKg = 80.0),
                        emptySet(),
                        mapOf(
                            1 to ClusterSegmentPatch(
                                ClusterSegmentTarget(4, 85.0),
                                setOf(ClusterSegmentField.LOAD)
                            )
                        )
                    )
                )
            )
        }
    }

    private fun entry(
        sets: Int = 3,
        reps: Int? = null,
        weightKg: Double? = null,
        cluster: ClusterPrescription? = null,
        setTargets: SlotSetTargets? = null
    ) = OccurrenceEntry(
        id = 9,
        position = 0,
        exerciseId = "squat",
        exerciseName = "Squat",
        movementPattern = MovementPattern.SQUAT,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        loadKind = LoadKind.EXTERNAL,
        cluster = cluster,
        setTargets = setTargets
    )
}
