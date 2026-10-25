package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.workout.GuidedExerciseProgress
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The presentation decisions behind the guided list: which completed exercise collapses, whether
 * an explicit expansion survives a refresh, and which paired member owns the round's controls.
 * These are pure state seams, so they are covered without a device or a running ViewModel.
 */
class GuidedPresentationTest {
    private val pair = Superset("arms", 10, 120)

    private fun entry(id: Long, performed: Int, sets: Int = 3, superset: Superset? = null) =
        GuidedExerciseProgress(
            occurrenceEntryId = id,
            exerciseId = "exercise-$id",
            exerciseName = "Exercise $id",
            prescribedSets = sets,
            performedWorkingSets = performed,
            reps = 10,
            weightKg = 10.0,
            loadKind = LoadKind.EXTERNAL,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            superset = superset
        )

    private fun paired(a: Int, b: Int, aSets: Int = 3, bSets: Int = 3) = GuidedWorkoutProgress(
        occurrenceId = 1,
        exercises = listOf(
            entry(10, a, aSets, pair),
            entry(20, b, bSets, pair)
        )
    )

    private fun single(performed: Int, sets: Int = 3) = GuidedWorkoutProgress(
        occurrenceId = 1,
        exercises = listOf(entry(30, performed, sets))
    )

    private fun state(progress: GuidedWorkoutProgress) = WorkoutLoggerUiState(
        guidedEnabled = true,
        guidedProgress = progress
    )

    private fun expandedIds(progress: GuidedWorkoutProgress): List<Long> =
        state(progress).let { current ->
            progress.exercises
                .filter(current::isGuidedEntryExpanded)
                .map { it.occurrenceEntryId }
        }

    @Test
    fun aCompletedExerciseCollapsesWithoutAnExplicitExpansion() {
        val progress = single(performed = 3, sets = 3)
        assertEquals(emptyList(), expandedIds(progress))
        assertFalse(state(progress).isGuidedEntryExpanded(progress.exercises.single()))
    }

    @Test
    fun anUnfinishedOrPartiallyLoggedExerciseNeverCollapses() {
        assertEquals(listOf(30L), expandedIds(single(performed = 0, sets = 3)))
        assertEquals(listOf(30L), expandedIds(single(performed = 2, sets = 3)))
    }

    @Test
    fun anExplicitExpansionPersistsUntilTheCompletionChanges() {
        val complete = single(performed = 3, sets = 3)
        val entry = complete.exercises.single()
        val expanded = state(complete).withGuidedExpansionToggled(entry)
        assertEquals(mapOf(30L to 3), expanded.guidedExpandedEntries)
        assertTrue(expanded.isGuidedEntryExpanded(entry))
        // Toggling the same completion again collapses it explicitly.
        assertFalse(expanded.withGuidedExpansionToggled(entry).isGuidedEntryExpanded(entry))

        // A refresh that records nothing new keeps the expansion for the same completion.
        val refreshed = expanded.withGuidedProgress(complete, sameOccurrence = true)
        assertEquals(mapOf(30L to 3), refreshed.guidedExpandedEntries)
        assertTrue(refreshed.isGuidedEntryExpanded(entry))

        // A changed completion drops it, and restoring the old count does not resurrect it.
        val changed = refreshed.withGuidedProgress(single(performed = 4, sets = 3), true)
        assertEquals(emptyMap(), changed.guidedExpandedEntries)
        val restored = changed.withGuidedProgress(complete, true)
        assertEquals(emptyMap(), restored.guidedExpandedEntries)
        assertFalse(restored.isGuidedEntryExpanded(entry))
    }

    @Test
    fun anUnfinishedExerciseIgnoresAnExpansionToggle() {
        val partial = single(performed = 2, sets = 3)
        val toggled = state(partial).withGuidedExpansionToggled(partial.exercises.single())
        assertEquals(emptyMap(), toggled.guidedExpandedEntries)
        assertTrue(toggled.isGuidedEntryExpanded(partial.exercises.single()))
    }

    @Test
    fun removingARecordedSetReexpandsTheCompletedExercise() {
        val complete = single(performed = 3, sets = 3)
        val entry = complete.exercises.single()
        val collapsed = state(complete).withGuidedExpansionToggled(entry)
        assertTrue(collapsed.isGuidedEntryExpanded(entry))

        val afterRemoval = collapsed.withGuidedProgress(single(performed = 2, sets = 3), true)
        assertEquals(emptyMap(), afterRemoval.guidedExpandedEntries)
        val remaining = afterRemoval.guidedProgress!!.exercises.single()
        assertTrue(afterRemoval.isGuidedEntryExpanded(remaining))
    }

    @Test
    fun switchingWorkoutDropsAnExplicitExpansion() {
        val complete = single(performed = 3, sets = 3)
        val entry = complete.exercises.single()
        val expanded = state(complete).withGuidedExpansionToggled(entry)

        val switched = expanded.withGuidedProgress(complete, sameOccurrence = false)
        assertEquals(emptyMap(), switched.guidedExpandedEntries)
        assertFalse(switched.isGuidedEntryExpanded(entry))
    }

    @Test
    fun onlyThePairedMemberWhoseTurnItIsShowsSetControls() {
        val progress = paired(a = 1, b = 0)
        val current = state(progress)
        val actions = progress.exercises.filter(current::showsGuidedActions)
            .map { it.occurrenceEntryId }
        // B is a set behind, so B owns the round and A keeps no action of its own.
        assertEquals(listOf(20L), actions)
        assertTrue(current.showsGuidedActions(progress.exercises[1]))
        assertFalse(current.showsGuidedActions(progress.exercises[0]))
    }

    @Test
    fun theRoundNeverExposesTwoLoggableMembers() {
        // Every step of a paired rotation keeps exactly one owner of the log/edit controls.
        listOf(
            0 to 0,
            1 to 0,
            1 to 1,
            2 to 1,
            3 to 2
        ).forEach { (a, b) ->
            val progress = paired(a = a, b = b)
            val current = state(progress)
            val owners = progress.exercises.filter(current::showsGuidedActions)
            assertEquals(1, owners.size, "performed $a/$b must keep one action owner")
        }
    }

    @Test
    fun anExplicitStartKeepsItsOwnControlsEvenInThePartnersTurn() {
        val progress = paired(a = 1, b = 0)
        val started = state(progress).copy(startedSetEntryId = 10)
        assertTrue(started.showsGuidedActions(progress.exercises[0]))
        assertFalse(started.showsGuidedActions(progress.exercises[1]))
    }

    @Test
    fun aCompleteOrUnpairedExerciseKeepsOrDropsControlsAsBefore() {
        val complete = single(performed = 3, sets = 3)
        assertFalse(state(complete).showsGuidedActions(complete.exercises.single()))
        val started = single(performed = 0, sets = 3)
        assertTrue(state(started).showsGuidedActions(started.exercises.single()))

        // Unpairing makes both members independent again, so each keeps its own controls.
        val unpaired = state(paired(a = 1, b = 0)).copy(supersetUnpairedKeys = setOf("arms"))
        assertTrue(unpaired.showsGuidedActions(unpaired.guidedProgress!!.exercises[0]))
        assertTrue(unpaired.showsGuidedActions(unpaired.guidedProgress!!.exercises[1]))
        assertFalse(unpaired.showsSupersetControls(unpaired.guidedProgress!!.exercises[0]))
        assertFalse(unpaired.showsSupersetControls(unpaired.guidedProgress!!.exercises[1]))
    }

    @Test
    fun skipMovesTheControlsAndResumeStaysReachable() {
        val progress = paired(a = 0, b = 0)
        val active = state(progress)
        assertTrue(active.showsSupersetControls(progress.exercises[0]))
        assertFalse(active.showsSupersetControls(progress.exercises[1]))

        val skipped = active.copy(supersetSkippedEntryId = 10)
        // The partner now holds the turn and its superset controls, which carry Resume.
        assertTrue(skipped.showsGuidedActions(progress.exercises[1]))
        assertFalse(skipped.showsGuidedActions(progress.exercises[0]))
        assertTrue(skipped.showsSupersetControls(progress.exercises[1]))
        assertFalse(skipped.showsSupersetControls(progress.exercises[0]))
    }

    @Test
    fun resumeRemainsReachableWhenThePairHasNoTurnLeft() {
        // A is skipped and B is already complete, so no member holds a turn.
        val progress = paired(a = 0, b = 3)
        val skipped = state(progress).copy(supersetSkippedEntryId = 10)
        assertFalse(skipped.showsGuidedActions(progress.exercises[0]))
        assertFalse(skipped.showsGuidedActions(progress.exercises[1]))
        assertTrue(skipped.showsSupersetControls(progress.exercises[0]))
        assertFalse(skipped.showsSupersetControls(progress.exercises[1]))
    }
}
