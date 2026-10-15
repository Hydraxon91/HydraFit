package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.EquipmentProfileContext
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.navigation.AppTab
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.unit = WeightUnit.LB
        fixture.savedRecords["old"] = PersonalRecord("old", 60.0, 5)
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        assertEquals(listOf("back-squat"), viewModel.state.value.eligibleExercises.map { it.id })
        assertEquals(
            listOf(fixture.savedRecords.getValue("old")),
            viewModel.state.value.existingRecords
        )
        fixture.unit = WeightUnit.KG
        // The entry unit is frozen when the PR step loaded; a later unit change cannot re-encode it.
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        viewModel.savePersonalRecordEntry()
        viewModel.next()
        runCurrent()
        assertEquals(
            WeightUnit.LB.displayToKilograms(100.0),
            fixture.savedRecords.getValue("back-squat").weightKg
        )
        assertEquals(
            listOf("back-squat"),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
                .map { it.exerciseId }
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

    @Test
    fun aHandoffCompletesSetupBeforeSignallingNavigation() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.FIRST_WORKOUT)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.handoff(AppTab.PLAN)
        runCurrent()
        // The handoff writes the setup boundary only: one commit, the final step's Continue input.
        assertEquals(1, fixture.attempts.size)
        assertEquals(
            SetupStepInput.Continue(SetupStep.FIRST_WORKOUT),
            fixture.attempts.single().input
        )
        assertEquals(SetupDisposition.COMPLETED, fixture.committed.value.disposition)
        assertEquals(SetupStep.FIRST_WORKOUT, fixture.committed.value.step)
        assertTrue(viewModel.state.value.finished)
        assertEquals(AppTab.PLAN, viewModel.state.value.pendingHandoff)
    }

    @Test
    fun aFailedHandoffCompletionStaysOnTheFinalStepWithoutNavigating() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.FIRST_WORKOUT)
        fixture.failure = IllegalStateException("injected")
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.handoff(AppTab.ROUTINES)
        runCurrent()
        assertNull(viewModel.state.value.pendingHandoff)
        assertFalse(viewModel.state.value.finished)
        assertFalse(viewModel.state.value.saving)
        assertEquals(OnboardingFailure.SAVE, viewModel.state.value.failure)
        assertEquals(SetupStep.FIRST_WORKOUT, viewModel.state.value.step)
        assertEquals(SetupDisposition.PENDING, fixture.committed.value.disposition)
        fixture.failure = null
        viewModel.handoff(AppTab.ROUTINES)
        runCurrent()
        assertNull(viewModel.state.value.failure)
        assertTrue(viewModel.state.value.finished)
        assertEquals(AppTab.ROUTINES, viewModel.state.value.pendingHandoff)
        assertEquals(SetupDisposition.COMPLETED, fixture.committed.value.disposition)
    }

    @Test
    fun aHandoffOnANonFinalStepWritesAndSignalsNothing() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.GOAL)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.handoff(AppTab.LOG)
        runCurrent()
        assertEquals(0, fixture.attempts.size)
        assertNull(viewModel.state.value.pendingHandoff)
        assertFalse(viewModel.state.value.finished)
        assertEquals(SetupStep.GOAL, viewModel.state.value.step)
        assertEquals(SetupDisposition.PENDING, fixture.committed.value.disposition)
    }

    @Test
    fun nextIsBlockedForAOneDayWeekdayDraftAndAllowsTwoToSix() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.WEEKDAYS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        assertEquals(SetupStepInput.Weekdays(null), viewModel.state.value.input)
        viewModel.edit(SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY)))
        viewModel.next()
        runCurrent()
        assertEquals(0, fixture.attempts.size)
        assertEquals(SetupStep.WEEKDAYS, fixture.committed.value.step)
        viewModel.edit(SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)))
        viewModel.next()
        runCurrent()
        assertEquals(
            SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            fixture.attempts.single().input
        )
        assertEquals(SetupStep.PERSONAL_RECORDS, fixture.committed.value.step)
    }

    @Test
    fun decideLaterSubmitsANullWeekdayChoice() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.committed.value = SetupState(step = SetupStep.WEEKDAYS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.edit(SetupStepInput.Weekdays(setOf(DayOfWeek.FRIDAY)))
        viewModel.edit(SetupStepInput.Weekdays(null))
        viewModel.next()
        runCurrent()
        assertEquals(SetupStepInput.Weekdays(null), fixture.attempts.single().input)
        assertEquals(SetupStep.PERSONAL_RECORDS, fixture.committed.value.step)
    }

    @Test
    fun typingAnEntryThenNextRecordsCanonicalKilogramsAndAdvances() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("225", "5")
        viewModel.next()
        runCurrent()
        // The frozen entry unit converts the typed value, exactly as an explicit Save would.
        assertEquals(
            PersonalRecord("back-squat", WeightUnit.LB.displayToKilograms(225.0), 5),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits.single()
        )
        assertEquals(
            WeightUnit.LB.displayToKilograms(225.0),
            fixture.savedRecords.getValue("back-squat").weightKg
        )
        assertEquals(SetupStep.FIRST_WORKOUT, viewModel.state.value.step)
        assertEquals(SetupStep.FIRST_WORKOUT, fixture.committed.value.step)
    }

    @Test
    fun typingAnEntryThenSkipRecordsNothingAndSetsSkipped() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        viewModel.skip()
        runCurrent()
        assertNull(fixture.attempts.single().input)
        assertTrue(fixture.recordWrites.isEmpty())
        assertTrue(fixture.savedRecords.isEmpty())
        assertEquals(SetupDisposition.SKIPPED, fixture.committed.value.disposition)
        assertTrue(viewModel.state.value.finished)
    }

    @Test
    fun aStagedEntryThenNextWritesExactlyOneRecordForThatExercise() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["old"] = PersonalRecord("old", 60.0, 5)
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        // "Save best set" stages immediately and keeps the entry open; Next folds it again.
        viewModel.savePersonalRecordEntry()
        viewModel.next()
        runCurrent()
        assertEquals(1, fixture.attempts.size)
        assertEquals(1, fixture.recordWrites.size)
        assertEquals(PersonalRecord("back-squat", 100.0, 5), fixture.recordWrites.single())
        assertEquals(60.0, fixture.savedRecords.getValue("old").weightKg)
        assertEquals(2, fixture.savedRecords.size)
    }

    @Test
    fun nextOnAnUntouchedKilogramEntryWritesNothing() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        val stored = PersonalRecord("back-squat", 100.0, 5)
        fixture.savedRecords["back-squat"] = stored
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        // Opening pre-fills exactly what the row already shows; Next must not rewrite the record.
        assertEquals("100", viewModel.state.value.personalRecordEntry?.weightText)
        viewModel.next()
        runCurrent()

        assertTrue(fixture.recordWrites.isEmpty())
        assertEquals(stored, fixture.savedRecords.getValue("back-squat"))
        assertEquals(
            emptyList(),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals(SetupStep.FIRST_WORKOUT, fixture.committed.value.step)
    }

    @Test
    fun nextOnAnUntouchedPoundEntryKeepsTheStoredKilograms() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["back-squat"] = PersonalRecord("back-squat", 100.0, 5)
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        // The pound display rounds 100.0 kg to 220.5 lb; submitting that untouched must not store
        // 220.5 lb back as 100.017117587213 kg over the record.
        assertEquals("220.5", viewModel.state.value.personalRecordEntry?.weightText)
        viewModel.next()
        runCurrent()

        assertTrue(fixture.recordWrites.isEmpty())
        assertEquals(100.0, fixture.savedRecords.getValue("back-squat").weightKg)
        assertEquals(5, fixture.savedRecords.getValue("back-squat").reps)
    }

    @Test
    fun aRepsOnlyEditKeepsTheExactStoredKilograms() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["back-squat"] = PersonalRecord("back-squat", 100.0, 5)
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("220.5", "6")
        viewModel.next()
        runCurrent()

        assertEquals(
            listOf(PersonalRecord("back-squat", 100.0, 6)),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals(100.0, fixture.savedRecords.getValue("back-squat").weightKg)
        assertEquals(6, fixture.savedRecords.getValue("back-squat").reps)
    }

    @Test
    fun aWeightOnlyEditKeepsTheStoredReps() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["back-squat"] = PersonalRecord("back-squat", 100.0, 5)
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("225", "5")
        viewModel.next()
        runCurrent()

        assertEquals(
            listOf(PersonalRecord("back-squat", WeightUnit.LB.displayToKilograms(225.0), 5)),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals(5, fixture.savedRecords.getValue("back-squat").reps)
    }

    @Test
    fun savingAnUntouchedEntryStagesAndWritesNothing() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        val stored = PersonalRecord("back-squat", 100.0, 5)
        fixture.savedRecords["back-squat"] = stored
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.savePersonalRecordEntry()

        // "Save best set" stages nothing, and Next then writes nothing at all.
        assertEquals(
            emptyList(),
            (viewModel.state.value.input as SetupStepInput.PersonalRecords).edits
        )
        viewModel.next()
        runCurrent()
        assertTrue(fixture.recordWrites.isEmpty())
        assertEquals(stored, fixture.savedRecords.getValue("back-squat"))
    }

    @Test
    fun savingARepsOnlyEditStagesTheSameRecordNextWrites() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["back-squat"] = PersonalRecord("back-squat", 100.0, 5)
        fixture.unit = WeightUnit.LB
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()

        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("220.5", "6")
        viewModel.savePersonalRecordEntry()

        // Save reads the entry by the same rule Next does, so it also keeps the exact kilograms.
        assertEquals(
            listOf(PersonalRecord("back-squat", 100.0, 6)),
            (viewModel.state.value.input as SetupStepInput.PersonalRecords).edits
        )
        viewModel.next()
        runCurrent()
        assertEquals(1, fixture.recordWrites.size)
        assertEquals(PersonalRecord("back-squat", 100.0, 6), fixture.recordWrites.single())
    }

    @Test
    fun invalidOrBlankTypingThenNextRecordsNothingAndStillAdvances() = runTest(dispatcher) {
        val cases = listOf(
            "" to "5",
            "100" to "",
            "0" to "5",
            "-5" to "5",
            "NaN" to "5",
            "heavy" to "5",
            "100" to "0",
            "100" to "lots"
        )
        cases.forEach { (weight, reps) ->
            val fixture = OnboardingFixture().withPrShortlist()
            fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
            val viewModel = fixture.viewModel(dispatcher)
            viewModel.open()
            runCurrent()
            viewModel.openPersonalRecordEntry("back-squat")
            viewModel.editPersonalRecordEntry(weight, reps)
            viewModel.next()
            runCurrent()
            val submitted = fixture.attempts.single().input as SetupStepInput.PersonalRecords
            assertEquals(emptyList(), submitted.edits, "recorded \"$weight\"/\"$reps\"")
            assertTrue(fixture.savedRecords.isEmpty())
            assertEquals(SetupStep.FIRST_WORKOUT, fixture.committed.value.step)
        }
    }

    @Test
    fun unknownAfterTypingRecordsNothingOnNext() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["old"] = PersonalRecord("old", 60.0, 5)
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        viewModel.dismissPersonalRecordEntry()
        assertNull(viewModel.state.value.personalRecordEntry)
        viewModel.next()
        runCurrent()
        assertEquals(
            emptyList(),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals(1, fixture.savedRecords.size)
        assertEquals(60.0, fixture.savedRecords.getValue("old").weightKg)
        assertEquals(SetupStep.FIRST_WORKOUT, fixture.committed.value.step)
    }

    @Test
    fun anOpenEntryForOneExerciseLeavesOtherDraftsAndStoredRecordsAlone() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.savedRecords["old"] = PersonalRecord("old", 60.0, 5)
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        // An exercise outside the eligible catalog can never open an entry.
        viewModel.openPersonalRecordEntry("bodyweight")
        assertNull(viewModel.state.value.personalRecordEntry)
        // Save stages a draft value, then closing the entry lets the row show it.
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("80", "8")
        viewModel.savePersonalRecordEntry()
        viewModel.dismissPersonalRecordEntry()
        // Opening continues from the value the row already shows.
        viewModel.openPersonalRecordEntry("back-squat")
        assertEquals("80", viewModel.state.value.personalRecordEntry?.weightText)
        assertEquals("8", viewModel.state.value.personalRecordEntry?.repsText)
        viewModel.editPersonalRecordEntry("90", "3")
        viewModel.next()
        runCurrent()
        assertEquals(
            PersonalRecord("back-squat", 90.0, 3),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits.single()
        )
        assertEquals(60.0, fixture.savedRecords.getValue("old").weightKg)
        assertEquals(2, fixture.savedRecords.size)
    }

    @Test
    fun reopeningAnOpenEntryKeepsItsTypedTextAndAReloadDiscardsIt() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        // Re-opening the same exercise keeps what was typed instead of re-seeding the entry.
        viewModel.openPersonalRecordEntry("back-squat")
        assertEquals("100", viewModel.state.value.personalRecordEntry?.weightText)
        assertEquals("5", viewModel.state.value.personalRecordEntry?.repsText)
        viewModel.back()
        runCurrent()
        assertNull(viewModel.state.value.personalRecordEntry)
        assertEquals(SetupStep.WEEKDAYS, viewModel.state.value.step)
        assertEquals(0, fixture.attempts.size)
    }

    @Test
    fun switchingEntriesStagesTheTypedRecordAndOpensTheNextOneClean() = runTest(dispatcher) {
        val fixture = OnboardingFixture().withPrShortlist()
        fixture.addEligibleExercise("overhead-press", "Overhead press")
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        viewModel.openPersonalRecordEntry("back-squat")
        viewModel.editPersonalRecordEntry("100", "5")
        // Opening another exercise stages what was typed as the first row's value instead of losing it.
        viewModel.openPersonalRecordEntry("overhead-press")
        assertEquals(
            listOf(PersonalRecord("back-squat", 100.0, 5)),
            (viewModel.state.value.input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals("overhead-press", viewModel.state.value.personalRecordEntry?.exerciseId)
        assertEquals("", viewModel.state.value.personalRecordEntry?.weightText)
        assertEquals("", viewModel.state.value.personalRecordEntry?.repsText)
        viewModel.next()
        runCurrent()
        assertEquals(
            listOf(PersonalRecord("back-squat", 100.0, 5)),
            (fixture.attempts.single().input as SetupStepInput.PersonalRecords).edits
        )
        assertEquals(100.0, fixture.savedRecords.getValue("back-squat").weightKg)
        assertEquals(1, fixture.recordWrites.size)
    }

    @Test
    fun blankOrInvalidEntryStagesNothingWhenAnotherEntryOpens() = runTest(dispatcher) {
        listOf("" to "5", "heavy" to "5").forEach { (weight, reps) ->
            val fixture = OnboardingFixture().withPrShortlist()
            fixture.addEligibleExercise("overhead-press", "Overhead press")
            fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
            val viewModel = fixture.viewModel(dispatcher)
            viewModel.open()
            runCurrent()
            viewModel.openPersonalRecordEntry("back-squat")
            viewModel.editPersonalRecordEntry(weight, reps)
            // Unparseable text stages nothing, so the switch is never blocked and nothing is invented.
            viewModel.openPersonalRecordEntry("overhead-press")
            assertEquals("overhead-press", viewModel.state.value.personalRecordEntry?.exerciseId)
            assertEquals(
                emptyList(),
                (viewModel.state.value.input as SetupStepInput.PersonalRecords).edits,
                "staged \"$weight\"/\"$reps\""
            )
            viewModel.next()
            runCurrent()
            assertTrue(fixture.savedRecords.isEmpty())
        }
    }

    @Test
    fun loadingEditingBackAndCloseWriteNothingOnEveryStep() = runTest(dispatcher) {
        SetupStep.entries.forEach { step ->
            val fixture = OnboardingFixture()
            fixture.committed.value = SetupState(step = step)
            val viewModel = fixture.viewModel(dispatcher)
            viewModel.open()
            runCurrent()
            assertEquals(step, viewModel.state.value.step)
            val draft = viewModel.state.value.input
            assertNotNull(draft)
            // What a render does (read state) plus an edit, Back and Close must never persist.
            viewModel.edit(draft)
            viewModel.back()
            runCurrent()
            viewModel.close()
            assertEquals(0, fixture.attempts.size)
        }
    }

    @Test
    fun fivePrBasicsKeepTheStepOrderWhateverTheCatalogReturns() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        // The catalog returns a different built-in first and the basics out of shortlist order.
        fixture.exercises = listOf(
            Exercise("seated-cable-row", "Seated cable row", emptySet(), emptySet()),
            Exercise("leg-press", "Leg press", emptySet(), emptySet()),
            Exercise("overhead-press", "Overhead press", emptySet(), emptySet()),
            Exercise("back-squat", "Back squat", emptySet(), emptySet()),
            Exercise("conventional-deadlift", "Conventional deadlift", emptySet(), emptySet()),
            Exercise("barbell-bench-press", "Barbell bench press", emptySet(), emptySet())
        )
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        assertEquals(
            listOf(
                "back-squat",
                "barbell-bench-press",
                "conventional-deadlift",
                "overhead-press",
                "seated-cable-row"
            ),
            viewModel.state.value.eligibleExercises.map { it.id }
        )
    }

    @Test
    fun prStepOmitsMissingIneligibleNonBasicAndCustomRows() = runTest(dispatcher) {
        val fixture = OnboardingFixture()
        fixture.exercises = listOf(
            // A custom row that happens to carry a shortlisted id is still a custom identity.
            Exercise(
                "seated-cable-row",
                "Seated cable row",
                emptySet(),
                emptySet(),
                isCustom = true
            ),
            Exercise("barbell-bench-press", "Barbell bench press", emptySet(), emptySet()),
            Exercise("user-my-lift", "My lift", emptySet(), emptySet(), isCustom = true),
            Exercise("leg-press", "Leg press", emptySet(), emptySet()),
            Exercise(
                "conventional-deadlift",
                "Conventional deadlift",
                emptySet(),
                emptySet(),
                loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
            ),
            Exercise("overhead-press", "Overhead press", emptySet(), emptySet())
        )
        fixture.committed.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val viewModel = fixture.viewModel(dispatcher)
        viewModel.open()
        runCurrent()
        // The absent back-squat, the bodyweight-only deadlift, the custom rows and the non-basic leg
        // press are omitted; what remains keeps the step's own order.
        assertEquals(
            listOf("barbell-bench-press", "overhead-press"),
            viewModel.state.value.eligibleExercises.map { it.id }
        )
        viewModel.openPersonalRecordEntry("user-my-lift")
        assertNull(viewModel.state.value.personalRecordEntry)
        // An offered basic still opens and edits normally.
        viewModel.openPersonalRecordEntry("overhead-press")
        assertEquals("overhead-press", viewModel.state.value.personalRecordEntry?.exerciseId)
    }

    @Test
    fun theEquipmentStepNamesTheActiveProfileAndKeepsItsContextAcrossAToggle() =
        runTest(dispatcher) {
            val fixture = OnboardingFixture()
            fixture.committed.value = SetupState(step = SetupStep.EQUIPMENT)
            val viewModel = fixture.viewModel(dispatcher)
            viewModel.open()
            runCurrent()

            // The step names the profile the selection below is written to.
            assertEquals("Home", viewModel.state.value.equipmentProfileName)
            val opened = viewModel.state.value.input as SetupStepInput.Equipment
            assertEquals(EquipmentProfileContext(1L, 0L), opened.profileContext)

            // The editor toggle keeps the draft and only flips one tag, so the captured context
            // survives instead of being reconstructed without it.
            viewModel.edit(opened.copy(selected = opened.selected + EquipmentTag.BARBELL))
            val edited = viewModel.state.value.input as SetupStepInput.Equipment
            assertEquals(setOf(EquipmentTag.BARBELL), edited.selected)
            assertEquals(opened.profileContext, edited.profileContext)
            assertEquals("Home", viewModel.state.value.equipmentProfileName)
        }
}

/**
 * The catalog the PR tests read: the one offered basic they edit plus the bodyweight-only row the
 * step must omit, so those tests exercise the step's shortlist instead of the whole catalog.
 */
private fun OnboardingFixture.withPrShortlist(): OnboardingFixture = apply {
    exercises = listOf(
        Exercise("back-squat", "Back squat", emptySet(), emptySet()),
        Exercise(
            "bodyweight",
            "Bodyweight",
            emptySet(),
            emptySet(),
            loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
        )
    )
}
