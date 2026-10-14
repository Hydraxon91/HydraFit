package com.hydrafit.app.core.domain.setup

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class SetupActionsTest {
    @Test
    fun everyNextSavesItsInputAndAdvancesExactlyOneBoundary() = runTest {
        val repository = FakeWriter()
        val save = SaveSetupStepUseCase(repository, StandardTestDispatcher(testScheduler))
        val inputs = listOf(
            SetupStepInput.Continue(SetupStep.WELCOME),
            SetupStepInput.Units(WeightUnit.LB),
            SetupStepInput.Equipment(emptySet()),
            SetupStepInput.Goal(TrainingGoal.STRENGTH),
            SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            SetupStepInput.PersonalRecords(listOf(PersonalRecord("external", 50.0, 5))),
            SetupStepInput.Continue(SetupStep.FIRST_WORKOUT)
        )
        inputs.forEachIndexed { index, input ->
            val saved = save(repository.state, input)
            assertEquals((index + 1).toLong(), saved.revision)
            assertEquals(SetupStep.entries[(index + 1).coerceAtMost(6)], saved.step)
        }
        assertEquals(inputs, repository.commands.map { it.input })
        assertEquals(SetupDisposition.COMPLETED, repository.state.disposition)
    }

    @Test
    fun skipHasNoStepPayloadAndRevisitDoesNotReenableAutomaticSetup() = runTest {
        val repository = FakeWriter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val skipped = SkipSetupUseCase(repository, dispatcher)(repository.state)
        assertEquals(SetupDisposition.SKIPPED, skipped.disposition)
        assertEquals(null, repository.commands.single().input)
        assertFailsWith<IllegalArgumentException> {
            SaveSetupStepUseCase(repository, dispatcher)(
                skipped,
                SetupStepInput.Units(WeightUnit.LB)
            )
        }
        val revisited = SaveSetupStepUseCase(repository, dispatcher)(
            skipped,
            SetupStepInput.Units(WeightUnit.LB),
            revisit = true
        )
        assertEquals(SetupDisposition.SKIPPED, revisited.disposition)
    }

    @Test
    fun invalidInputsNeverReachTheWriter() = runTest {
        val repository = FakeWriter()
        val save = SaveSetupStepUseCase(repository, StandardTestDispatcher(testScheduler))
        val badRecords = listOf(
            PersonalRecord("external", Double.NaN, 5),
            PersonalRecord("external", 0.0, 5),
            PersonalRecord("external", -1.0, 5),
            PersonalRecord("external", 1.0, 0),
            PersonalRecord("external", 1.0, 5, LoadKind.ADDED)
        )
        badRecords.forEach { record ->
            assertFailsWith<IllegalArgumentException> {
                save(repository.state, SetupStepInput.PersonalRecords(listOf(record)))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            save(repository.state, SetupStepInput.Weekdays(emptySet()))
        }
        assertFailsWith<IllegalArgumentException> {
            save(
                repository.state,
                SetupStepInput.Equipment(emptySet(), mapOf(EquipmentTag("DUMBBELL") to -1.0))
            )
        }
        assertEquals(0, repository.commands.size)
    }

    @Test
    fun failureAndCancellationDoNotAdvanceAndRetryUsesSameExpectedBoundary() = runTest {
        val repository = FakeWriter()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val save = SaveSetupStepUseCase(repository, dispatcher)
        val initial = repository.state
        repository.failure = IllegalStateException("injected")
        assertFailsWith<IllegalStateException> {
            save(initial, SetupStepInput.Units(WeightUnit.LB))
        }
        assertEquals(initial, repository.state)
        repository.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { SkipSetupUseCase(repository, dispatcher)(initial) }
        assertEquals(initial, repository.state)
        repository.failure = null
        assertEquals(SetupStep.EQUIPMENT, save(initial, SetupStepInput.Units(WeightUnit.LB)).step)
    }

    @Test
    fun collectionSnapshotDoesNotFollowLaterEditorMutations() {
        val selected = mutableSetOf(EquipmentTag("DUMBBELL"))
        val weights = mutableMapOf(EquipmentTag("DUMBBELL") to 30.0)
        val snapshot = SetupStepInput.Equipment(selected, weights).snapshot()
        selected.clear()
        weights.clear()
        assertEquals(
            SetupStepInput.Equipment(
                setOf(EquipmentTag("DUMBBELL")),
                mapOf(EquipmentTag("DUMBBELL") to 30.0)
            ),
            snapshot
        )
    }

    private class FakeWriter : SetupWriteRepository {
        var state = SetupState()
        var failure: Exception? = null
        val commands = mutableListOf<SetupCommit>()

        override suspend fun commit(command: SetupCommit): SetupState {
            failure?.let { throw it }
            if (state != command.expected) throw StaleSetupContextException()
            commands += command
            state = command.nextState()
            return state
        }
    }
}
