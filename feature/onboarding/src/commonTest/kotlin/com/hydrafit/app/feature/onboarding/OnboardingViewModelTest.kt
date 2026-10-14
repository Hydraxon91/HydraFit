package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun constructionOpeningEditingBackAndCloseWriteNothing() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        val viewModel = fixture.viewModel(dispatcher)
        assertNull(viewModel.state.value.committed)
        viewModel.open()
        runCurrent()
        viewModel.edit(SetupStepInput.Units(WeightUnit.LB))
        viewModel.back()
        runCurrent()
        assertEquals(SetupStep.WELCOME, viewModel.state.value.step)
        viewModel.close()
        assertEquals(0, fixture.attempts.size)
        assertEquals(SetupStep.UNITS, fixture.committed.value.step)
        assertEquals(WeightUnit.KG, fixture.unit)
    }

    @Test
    fun repeatNextIsGuardedAcrossWriteAndSuccessStepLoading() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.writeGate = CompletableDeferred()
        fixture.unitGate = CompletableDeferred()
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.next()
        viewModel.next()
        viewModel.skip()
        runCurrent()
        assertEquals(1, fixture.attempts.size)
        fixture.writeGate!!.complete(Unit)
        runCurrent()
        assertTrue(viewModel.state.value.saving)
        viewModel.next()
        assertEquals(1, fixture.attempts.size)
        fixture.unitGate!!.complete(Unit)
        runCurrent()
        assertEquals(SetupStep.UNITS, viewModel.state.value.step)
        assertFalse(viewModel.state.value.saving)
        assertEquals(1, fixture.attempts.size)
    }

    @Test
    fun failedSaveRetainsEditsAndRetryAdvancesOnlyAfterSuccess() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        fixture.failure = IllegalStateException("injected")
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        val input = SetupStepInput.Units(WeightUnit.LB)
        viewModel.edit(input)
        viewModel.next()
        runCurrent()
        assertEquals(input, viewModel.state.value.input)
        assertEquals(OnboardingFailure.SAVE, viewModel.state.value.failure)
        assertEquals(SetupStep.UNITS, fixture.committed.value.step)
        fixture.failure = null
        viewModel.next()
        runCurrent()
        assertEquals(WeightUnit.LB, fixture.unit)
        assertEquals(SetupStep.EQUIPMENT, fixture.committed.value.step)
        assertEquals(fixture.attempts[0], fixture.attempts[1])
    }

    @Test
    fun skipIgnoresUnsavedInputsAndCompletionRequiresNoWorkout() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.edit(SetupStepInput.Units(WeightUnit.LB))
        viewModel.skip()
        runCurrent()
        assertNull(fixture.attempts.single().input)
        assertEquals(WeightUnit.KG, fixture.unit)
        assertEquals(SetupDisposition.SKIPPED, fixture.committed.value.disposition)
        assertTrue(viewModel.state.value.finished)
        fixture.committed.value = SetupState(step = SetupStep.FIRST_WORKOUT)
        viewModel.open()
        runCurrent()
        viewModel.next()
        runCurrent()
        assertEquals(SetupDisposition.COMPLETED, fixture.committed.value.disposition)
        assertTrue(viewModel.state.value.finished)
    }

    @Test
    fun returningUsersCanRevisitButDoNotAutomaticallyOpen() = runTest(dispatcher) {
        val dispositions = listOf(
            SetupDisposition.RETURNING,
            SetupDisposition.SKIPPED,
            SetupDisposition.COMPLETED
        )
        dispositions.forEach { disposition ->
            val fixture = OnboardingFixture()
            fixture.committed.value = SetupState(disposition = disposition, step = SetupStep.GOAL)
            val viewModel = fixture.viewModel(dispatcher)
            viewModel.open()
            runCurrent()
            assertTrue(viewModel.state.value.finished)
            viewModel.open(revisit = true)
            runCurrent()
            assertFalse(viewModel.state.value.finished)
            assertEquals(SetupStep.WELCOME, viewModel.state.value.step)
            viewModel.next()
            runCurrent()
            assertEquals(disposition, fixture.committed.value.disposition)
        }
    }

    @Test
    fun readinessGatesResumeAndRecreationDiscardsOnlyUnsavedDrafts() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.ready.value = false
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        val first = fixture.viewModel(dispatcher)
        first.open()
        runCurrent()
        assertTrue(first.state.value.loading)
        fixture.ready.value = true
        runCurrent()
        first.edit(SetupStepInput.Units(WeightUnit.LB))
        first.close()
        val recreated = fixture.viewModel(dispatcher)
        recreated.open()
        runCurrent()
        assertEquals(SetupStepInput.Units(WeightUnit.KG), recreated.state.value.input)
        assertEquals(0, fixture.attempts.size)
    }

    @Test
    fun reopeningDuringAWriteWaitsAndOldSuccessCannotFinishTheNewJourney() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        fixture.writeGate = CompletableDeferred()
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.next()
        runCurrent()
        viewModel.close()
        viewModel.open(revisit = true)
        runCurrent()
        assertTrue(viewModel.state.value.loading)
        viewModel.next()
        fixture.writeGate!!.complete(Unit)
        runCurrent()
        assertEquals(1, fixture.attempts.size)
        assertEquals(SetupStep.WELCOME, viewModel.state.value.step)
        assertEquals(fixture.committed.value, viewModel.state.value.committed)
        assertFalse(viewModel.state.value.finished)
    }

    @Test
    fun successfulWriteThenFailedLoadRetriesOnlyTheReadNotTheSave() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.unitFailure = IllegalStateException("read failed")
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.next()
        runCurrent()
        assertEquals(OnboardingFailure.LOAD, viewModel.state.value.failure)
        assertEquals(SetupStep.UNITS, fixture.committed.value.step)
        viewModel.next()
        assertEquals(1, fixture.attempts.size)
        fixture.unitFailure = null
        viewModel.retryLoad()
        runCurrent()
        assertNull(viewModel.state.value.failure)
        assertEquals(SetupStep.UNITS, viewModel.state.value.step)
        assertEquals(1, fixture.attempts.size)
    }

    @Test
    fun prEditsUseFrozenUnitsAndOmittingExistingRecordsDoesNotDeleteThem() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.unit = WeightUnit.LB
        fixture.savedRecords["old"] = PersonalRecord("old", 60.0, 5)
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        assertEquals(listOf("external"), viewModel.state.value.eligibleExercises.map { it.id })
        assertEquals(
            listOf(fixture.savedRecords.getValue("old")),
            viewModel.state.value.existingRecords
        )
        fixture.unit = WeightUnit.KG
        viewModel.editPersonalRecord("external", 100.0, 5)
        viewModel.editPersonalRecord("bodyweight", 100.0, 5)
        viewModel.next()
        runCurrent()
        assertEquals(
            WeightUnit.LB.displayToKilograms(100.0),
            fixture.savedRecords.getValue("external").weightKg
        )
        assertEquals(60.0, fixture.savedRecords.getValue("old").weightKg)
        assertEquals(2, fixture.savedRecords.size)
    }

    @Test
    fun failedResumeReadCanRetryOrSkipWithoutSavingInputs() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        fixture.unitFailure = IllegalStateException("read failed")
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        assertEquals(SetupStep.UNITS, viewModel.state.value.step)
        assertEquals(OnboardingFailure.LOAD, viewModel.state.value.failure)
        fixture.unitFailure = null
        viewModel.retryLoad()
        runCurrent()
        assertEquals(SetupStepInput.Units(WeightUnit.KG), viewModel.state.value.input)
        viewModel.close()
        fixture.unitFailure = IllegalStateException("read failed")
        viewModel.open()
        runCurrent()
        viewModel.skip()
        runCurrent()
        assertEquals(SetupDisposition.SKIPPED, fixture.committed.value.disposition)
        assertNull(fixture.attempts.single().input)
    }

    @Test
    fun anExternalBoundaryChangeRejectsTheStaleEditorWithoutAdvancing() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.UNITS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        val changed = fixture.committed.value.copy(step = SetupStep.GOAL, revision = 1L)
        fixture.committed.value = changed
        val input = SetupStepInput.Units(WeightUnit.LB)
        viewModel.edit(input)
        viewModel.next()
        runCurrent()
        assertEquals(OnboardingFailure.STALE_CONTEXT, viewModel.state.value.failure)
        assertEquals(input, viewModel.state.value.input)
        assertEquals(changed, fixture.committed.value)
        assertEquals(WeightUnit.KG, fixture.unit)
        viewModel.open()
        runCurrent()
        assertEquals(SetupStep.GOAL, viewModel.state.value.step)
    }

    @Test
    fun cancellationIsNotAnIoFailureAndLeavesTheCommittedBoundaryUnchanged() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.failure = CancellationException("cancelled")
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.next()
        runCurrent()
        assertEquals(SetupState(), fixture.committed.value)
        assertNull(viewModel.state.value.failure)
        assertFalse(viewModel.state.value.saving)
    }
}
