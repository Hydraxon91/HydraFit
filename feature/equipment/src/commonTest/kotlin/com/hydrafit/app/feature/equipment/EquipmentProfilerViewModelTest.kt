package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExerciseProfile
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
import com.hydrafit.app.core.userdata.equipment.CustomExerciseFailureReason
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentProfilerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsSelectionCatalogAndExercises() = runTest(dispatcher) {
        val viewModel = viewModel(selection = FakeSelectionRepository(setOf(EquipmentTag.BARBELL)))
        advanceUntilIdle()

        assertEquals(setOf(EquipmentTag.BARBELL), viewModel.state.value.selectedTags)
        assertEquals(
            listOf(EquipmentTag.BARBELL, EquipmentTag.DUMBBELL),
            viewModel.state.value.equipment.map { it.id }
        )
        assertEquals(listOf("Back Squat"), viewModel.state.value.builtInExercises.map { it.name })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun editorFlagsAMismatchedMovementPattern() {
        val mismatched = ExerciseEditorState(
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.QUADS to 1.0)
        )
        val coherent = ExerciseEditorState(
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 1.0, MuscleGroup.TRICEPS to 0.4)
        )

        assertTrue(mismatched.patternMismatch)
        assertFalse(coherent.patternMismatch)
    }

    @Test
    fun togglingPersistsSelection() = runTest(dispatcher) {
        val selection = FakeSelectionRepository(emptySet())
        val viewModel = viewModel(selection = selection)
        advanceUntilIdle()

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()

        assertEquals(setOf(EquipmentTag.DUMBBELL), selection.stored)
    }

    @Test
    fun managesCustomEquipmentRenameKeepingTheId() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val viewModel = viewModel(equipment = equipment)
        advanceUntilIdle()
        val customId = equipment.add("Trap Bar").id
        advanceUntilIdle()

        viewModel.onManageEquipment(customId)
        viewModel.onRenameEquipmentNameChanged("Hex Bar")
        viewModel.onSaveEquipmentRenamed()
        advanceUntilIdle()

        assertEquals(customId, viewModel.state.value.equipment.single { it.name == "Hex Bar" }.id)
    }

    @Test
    fun surfacesRenameFailureAndKeepsTheEquipment() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository(renameFailure = "Name already used")
        val viewModel = viewModel(equipment = equipment)
        advanceUntilIdle()
        val customId = equipment.add("Trap Bar").id
        advanceUntilIdle()

        viewModel.onManageEquipment(customId)
        viewModel.onRenameEquipmentNameChanged("Hex Bar")
        viewModel.onSaveEquipmentRenamed()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.equipmentEditor.error)
        assertTrue(viewModel.state.value.equipment.any { it.id == customId })
    }

    @Test
    fun surfacesAddFailureFromTheRepository() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository(addFailure = "Already exists")
        val viewModel = viewModel(equipment = equipment)
        advanceUntilIdle()

        viewModel.onNewEquipmentNameChanged("Trap Bar")
        viewModel.onAddEquipment()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.newEquipmentError)
        assertTrue(viewModel.state.value.equipment.none { it.name == "Trap Bar" })
    }

    @Test
    fun setsAMaxWeightOnABuiltInEquipment() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val viewModel = viewModel(equipment = equipment)
        advanceUntilIdle()

        viewModel.onManageEquipment(EquipmentTag.DUMBBELL)
        assertTrue(viewModel.state.value.equipmentEditor.isBuiltIn)
        viewModel.onMaxWeightChanged("100")
        viewModel.onSaveEquipmentRenamed()
        advanceUntilIdle()

        assertEquals(
            100.0,
            equipment.all().first { it.id == EquipmentTag.DUMBBELL }.maxWeightKg
        )
    }

    @Test
    fun openingABuiltInEditorSeedsCurrentOverridesAndSavesThem() = runTest(dispatcher) {
        val overrides = FakeExerciseOverrideRepository()
        val viewModel = viewModel(overrides = overrides)
        advanceUntilIdle()

        viewModel.onEditExercise("back-squat")
        assertEquals(setOf(EquipmentTag.BARBELL), viewModel.state.value.exerciseEditor.equipment)
        assertEquals(
            setOf(MuscleGroup.QUADS),
            viewModel.state.value.exerciseEditor.primaryMuscles
        )

        viewModel.onEditorNameChanged("Back Squat (Low Bar)")
        viewModel.onEditorEquipmentToggled(EquipmentTag.BENCH)
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.CHEST_UPPER, 1.0)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        val stored = requireNotNull(overrides.overrides["back-squat"])
        assertEquals("Back Squat (Low Bar)", stored.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), stored.equipment)
        assertEquals(1.0, stored.involvements[MuscleGroup.QUADS])
        assertEquals(1.0, stored.involvements[MuscleGroup.CHEST_UPPER])
        assertNull(viewModel.state.value.exerciseEditor.exerciseId)
    }

    @Test
    fun seedsAndSavesTheUnilateralFlag() = runTest(dispatcher) {
        val overrides = FakeExerciseOverrideRepository()
        overrides.update(
            exerciseId = "back-squat",
            name = null,
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            movementPattern = MovementPattern.SQUAT,
            unilateral = true,
            involvements = mapOf(MuscleGroup.QUADS to 1.0, MuscleGroup.GLUTES to 1.0)
        )
        val custom = FakeCustomExerciseRepository()
        val viewModel = viewModel(overrides = overrides, custom = custom)
        advanceUntilIdle()

        viewModel.onEditExercise("back-squat")
        assertTrue(viewModel.state.value.exerciseEditor.isUnilateral)
        viewModel.onEditorUnilateralToggled(false)
        viewModel.onSaveExercise()
        advanceUntilIdle()
        assertEquals(false, overrides.overrides["back-squat"]?.unilateral)

        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("My Row")
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.LATS, 1.0)
        viewModel.onEditorUnilateralToggled(true)
        viewModel.onSaveExercise()
        advanceUntilIdle()
        assertTrue(custom.created.single().isUnilateral)
    }

    @Test
    fun resetClearsBuiltInOverrides() = runTest(dispatcher) {
        val overrides = FakeExerciseOverrideRepository()
        overrides.update(
            exerciseId = "back-squat",
            name = "Renamed",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            movementPattern = MovementPattern.SQUAT,
            unilateral = null,
            involvements = mapOf(MuscleGroup.QUADS to 1.0)
        )
        val viewModel = viewModel(overrides = overrides)
        advanceUntilIdle()

        viewModel.onEditExercise("back-squat")
        viewModel.onResetExercise()
        advanceUntilIdle()

        assertTrue(overrides.overrides.isEmpty())
    }

    @Test
    fun aMuscleHoldsASingleInvolvementWeight() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEditExercise("back-squat")

        // GLUTES is seeded secondary (0.5); raising it to the primary tier (1.0) replaces the value.
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.GLUTES, 1.0)

        val editor = viewModel.state.value.exerciseEditor
        assertEquals(1.0, editor.involvements.getValue(MuscleGroup.GLUTES))
        assertTrue(MuscleGroup.GLUTES in editor.primaryMuscles)
        assertFalse(MuscleGroup.GLUTES in editor.secondaryMuscles)

        // Clearing removes the muscle entirely.
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.GLUTES, null)
        assertFalse(MuscleGroup.GLUTES in viewModel.state.value.exerciseEditor.involvements)
    }

    @Test
    fun createsACustomExerciseThroughTheEditor() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository()
        val viewModel = viewModel(custom = custom)
        advanceUntilIdle()

        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Trap Bar Deadlift")
        viewModel.onEditorPatternChanged(MovementPattern.HINGE)
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.LATS, 1.0)
        assertEquals(MovementPattern.HINGE, viewModel.state.value.exerciseEditor.movementPattern)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        val created = custom.created.single()
        assertEquals("Trap Bar Deadlift", created.name)
        assertNull(viewModel.state.value.exerciseEditor.exerciseId)
    }

    @Test
    fun cannotSaveWithoutANameOrPrimaryMuscle() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNewCustomExercise()

        assertFalse(viewModel.state.value.exerciseEditor.canSave)
        viewModel.onEditorNameChanged("Something")
        assertFalse(viewModel.state.value.exerciseEditor.canSave)
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.ABS, 1.0)

        assertTrue(viewModel.state.value.exerciseEditor.canSave)
    }

    @Test
    fun surfacesDeleteFailureFromTheRepository() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository(deleteFailure = "This exercise has logged sets")
        val viewModel = viewModel(custom = custom)
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Disposable")
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.ABS, 1.0)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        viewModel.onEditExercise(custom.created.single().id)
        viewModel.onDeleteCustomExercise()
        advanceUntilIdle()

        assertTrue(
            requireNotNull(viewModel.state.value.exerciseEditor.error).contains("logged sets")
        )
    }

    @Test
    fun searchFiltersTheExerciseList() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onSearchChanged("squat")

        assertTrue(viewModel.state.value.visibleExercises.all { it.name.contains("squat", true) })
        viewModel.onSearchChanged("zzz")
        assertTrue(viewModel.state.value.visibleExercises.isEmpty())
    }

    @Test
    fun searchMatchesHyphenatedAndSpaceSeparatedNames() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Close-grip Pulldown")
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.LATS, 1.0)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        viewModel.onSearchChanged("close grip")
        assertEquals(
            listOf("Close-grip Pulldown"),
            viewModel.state.value.visibleExercises.map { it.name }
        )

        viewModel.onSearchChanged("back-squat")
        assertEquals(
            listOf("Back Squat"),
            viewModel.state.value.visibleExercises.map { it.name }
        )

        viewModel.onSearchChanged("-")
        assertEquals(2, viewModel.state.value.visibleExercises.size)
    }

    @Test
    fun savesAndClearsAPersonalRecord() = runTest(dispatcher) {
        val records = FakePersonalRecordRepository()
        val viewModel = viewModel(records = records)
        advanceUntilIdle()

        viewModel.onNewPersonalRecord()
        viewModel.onPersonalRecordExerciseSelected("back-squat")
        viewModel.onPersonalRecordWeightChanged("120")
        viewModel.onPersonalRecordRepsChanged("5")
        viewModel.onSavePersonalRecord()
        advanceUntilIdle()

        val row = viewModel.state.value.personalRecords.single()
        assertEquals("Back Squat", row.exerciseName)
        assertEquals(120.0, row.weightKg)
        assertEquals(5, row.reps)

        viewModel.onClearPersonalRecord("back-squat")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.personalRecords.isEmpty())
    }

    @Test
    fun surfacesPersonalRecordSaveFailure() = runTest(dispatcher) {
        val records = FakePersonalRecordRepository(setFailure = "Disk full")
        val viewModel = viewModel(records = records)
        advanceUntilIdle()

        viewModel.onNewPersonalRecord()
        viewModel.onPersonalRecordExerciseSelected("back-squat")
        viewModel.onPersonalRecordWeightChanged("120")
        viewModel.onPersonalRecordRepsChanged("5")
        viewModel.onSavePersonalRecord()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.personalRecordEditor.error)
        assertTrue(viewModel.state.value.personalRecords.isEmpty())
    }

    @Test
    fun findIsExplicitCreationOnlyAndApplyDoesNotSaveOrRename() = runTest(dispatcher) {
        val suggested = suggestedProfile()
        val catalog = ProfileCatalog(listOf(suggested))
        val custom = FakeCustomExerciseRepository()
        val overrides = FakeExerciseOverrideRepository()
        val records = FakePersonalRecordRepository()
        val viewModel = viewModel(
            catalog = catalog,
            custom = custom,
            overrides = overrides,
            records = records
        )
        advanceUntilIdle()
        viewModel.onFindProfile()
        assertEquals(0, catalog.reads)
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("  Langhantel-Bankdrücken  ")
        assertNull(viewModel.state.value.exerciseEditor.suggestion.preview)
        assertTrue(viewModel.state.value.exerciseEditor.involvements.isEmpty())
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertEquals(suggested, viewModel.state.value.exerciseEditor.suggestion.preview)
        assertTrue(custom.created.isEmpty())
        viewModel.onApplyProfile()
        val editor = viewModel.state.value.exerciseEditor
        assertEquals("  Langhantel-Bankdrücken  ", editor.name)
        assertEquals(suggested.profile.involvements, editor.involvements)
        assertEquals(suggested.profile.equipment, editor.equipment)
        assertTrue(custom.created.isEmpty())
        assertTrue(overrides.overrides.isEmpty())
        assertTrue(records.observe().first().isEmpty())
        viewModel.onSaveExercise()
        advanceUntilIdle()
        assertEquals(1, custom.created.size)
        assertEquals(suggested.profile.involvements, custom.created.single().involvements)
        assertEquals(ExerciseEditorState(), viewModel.state.value.exerciseEditor)
        viewModel.onEditExercise(suggested.catalogId)
        assertFalse(viewModel.state.value.exerciseEditor.isNew)
        assertEquals(suggested.catalogId, viewModel.state.value.exerciseEditor.exerciseId)
        viewModel.onFindProfile()
        assertEquals(1, catalog.reads)
    }

    @Test
    fun manualInteractionsStayTouchedEvenForDefaultsAndRestoredValues() = runTest(dispatcher) {
        val viewModel = viewModel(catalog = ProfileCatalog(listOf(suggestedProfile())))
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Bench")
        viewModel.onEditorPatternChanged(MovementPattern.CORE)
        viewModel.onEditorLoadCapabilityChanged(ExerciseLoadCapability.EXTERNAL)
        viewModel.onEditorUnilateralToggled(false)
        viewModel.onEditorEquipmentToggled(EquipmentTag.DUMBBELL)
        viewModel.onEditorEquipmentToggled(EquipmentTag.DUMBBELL)
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.ABS, 1.0)
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.ABS, null)
        val before = viewModel.state.value.exerciseEditor
        assertEquals(ExerciseProfileGroup.entries.toSet(), before.touchedGroups)
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.exerciseEditor.suggestion.selectedGroups.isEmpty())
        val preview = viewModel.state.value.exerciseEditor
        viewModel.onApplyProfile()
        assertEquals(preview, viewModel.state.value.exerciseEditor)
        viewModel.onProfileGroupToggled(ExerciseProfileGroup.INVOLVEMENTS)
        assertEquals(before.involvements, viewModel.state.value.exerciseEditor.involvements)
        assertEquals(before.touchedGroups, viewModel.state.value.exerciseEditor.touchedGroups)
        viewModel.onDismissProfileSuggestion()
        assertEquals(before, viewModel.state.value.exerciseEditor)
    }

    @Test
    fun partialApplyAndNameInvalidationPreserveProtection() = runTest(dispatcher) {
        val viewModel = viewModel(catalog = ProfileCatalog(listOf(suggestedProfile())))
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Bench")
        viewModel.onEditorPatternChanged(MovementPattern.HINGE)
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertFalse(
            ExerciseProfileGroup.PATTERN in
                viewModel.state.value.exerciseEditor.suggestion.selectedGroups
        )
        viewModel.onProfileGroupToggled(ExerciseProfileGroup.LOAD)
        viewModel.onApplyProfile()
        val applied = viewModel.state.value.exerciseEditor
        assertEquals(MovementPattern.HINGE, applied.movementPattern)
        assertEquals(ExerciseLoadCapability.EXTERNAL, applied.loadCapability)
        assertEquals(suggestedProfile().profile.involvements, applied.involvements)
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertEquals(
            setOf(ExerciseProfileGroup.LOAD),
            viewModel.state.value.exerciseEditor.suggestion.selectedGroups
        )
        viewModel.onEditorNameChanged("Unknown name")
        assertEquals(applied.copy(name = "Unknown name"), viewModel.state.value.exerciseEditor)
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.exerciseEditor.suggestion.noMatch)
        assertEquals(applied.involvements, viewModel.state.value.exerciseEditor.involvements)
        viewModel.onDismissExerciseEditor()
        viewModel.onNewCustomExercise()
        assertEquals(
            ExerciseEditorState(isNew = true, isCustom = true),
            viewModel.state.value.exerciseEditor
        )
    }

    @Test
    fun ambiguityRequiresExplicitValidChoiceAndChooserCancelDoesNothing() = runTest(dispatcher) {
        val first = suggestedProfile()
        val second = first.copy(catalogId = "other", canonicalName = "Incline Bench")
        val viewModel = viewModel(catalog = ProfileCatalog(listOf(first, second)))
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Bench")
        val before = viewModel.state.value.exerciseEditor
        viewModel.onFindProfile()
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.exerciseEditor.suggestion.candidates.size)
        assertNull(viewModel.state.value.exerciseEditor.suggestion.preview)
        viewModel.onApplyProfile()
        viewModel.onProfileCandidateSelected("missing")
        assertNull(viewModel.state.value.exerciseEditor.suggestion.preview)
        viewModel.onDismissProfileSuggestion()
        assertEquals(before, viewModel.state.value.exerciseEditor)
        viewModel.onFindProfile()
        advanceUntilIdle()
        viewModel.onProfileCandidateSelected("other")
        assertEquals(second, viewModel.state.value.exerciseEditor.suggestion.preview)
    }

    @Test
    fun delayedFindCannotAttachAfterNameChangeOrCancelReopen() = runTest(dispatcher) {
        listOf(false, true).forEach { reopen ->
            val gate = CompletableDeferred<List<CatalogExerciseProfile>>()
            val viewModel = viewModel(catalog = ProfileCatalog(emptyList(), gate))
            advanceUntilIdle()
            viewModel.onNewCustomExercise()
            viewModel.onEditorNameChanged("Bench")
            viewModel.onFindProfile()
            runCurrent()
            if (reopen) {
                viewModel.onDismissExerciseEditor()
                viewModel.onNewCustomExercise()
                viewModel.onEditorNameChanged("Bench")
            } else {
                viewModel.onEditorNameChanged("Different")
            }
            val before = viewModel.state.value.exerciseEditor
            gate.complete(listOf(suggestedProfile()))
            advanceUntilIdle()
            assertEquals(before, viewModel.state.value.exerciseEditor)
        }
    }

    @Test
    fun delayedFindUsesLatestTouchesAndFailureRemainsRetryable() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<CatalogExerciseProfile>>()
        val catalog = ProfileCatalog(emptyList(), gate)
        val viewModel = viewModel(catalog = catalog)
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Bench")
        viewModel.onFindProfile()
        runCurrent()
        viewModel.onEditorPatternChanged(MovementPattern.CORE)
        gate.complete(listOf(suggestedProfile()))
        advanceUntilIdle()
        assertFalse(
            ExerciseProfileGroup.PATTERN in
                viewModel.state.value.exerciseEditor.suggestion.selectedGroups
        )
        val failingCatalog = ProfileCatalog(
            listOf(suggestedProfile()),
            failure = IllegalStateException("read failure")
        )
        val failing = viewModel(catalog = failingCatalog)
        advanceUntilIdle()
        failing.onNewCustomExercise()
        failing.onEditorNameChanged("Bench")
        failing.onFindProfile()
        advanceUntilIdle()
        assertTrue(failing.state.value.exerciseEditor.suggestion.failed)
        failingCatalog.failure = null
        failing.onFindProfile()
        advanceUntilIdle()
        assertNotNull(failing.state.value.exerciseEditor.suggestion.preview)
    }

    @Test
    fun allCapabilitiesSurviveApplyAndCancel() = runTest(dispatcher) {
        ExerciseLoadCapability.entries.forEach { capability ->
            val custom = FakeCustomExerciseRepository()
            val viewModel = viewModel(
                custom = custom,
                catalog = ProfileCatalog(listOf(suggestedProfile(capability)))
            )
            advanceUntilIdle()
            viewModel.onNewCustomExercise()
            viewModel.onEditorNameChanged("Bench")
            viewModel.onFindProfile()
            advanceUntilIdle()
            assertEquals(
                capability,
                viewModel.state.value.exerciseEditor.suggestion.preview?.profile?.loadCapability
            )
            viewModel.onApplyProfile()
            assertEquals(capability, viewModel.state.value.exerciseEditor.loadCapability)
            viewModel.onDismissExerciseEditor()
            advanceUntilIdle()
            assertTrue(custom.created.isEmpty())
        }
    }

    @Test
    fun nameConflictIsTypedAndClearedByNameEditsWithoutDiscardingProfile() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository(
            addFailure = CustomExerciseException(
                "duplicate",
                CustomExerciseFailureReason.NAME_CONFLICT
            )
        )
        val viewModel = viewModel(
            custom = custom,
            catalog = ProfileCatalog(listOf(suggestedProfile()))
        )
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Bench")
        viewModel.onFindProfile()
        advanceUntilIdle()
        viewModel.onApplyProfile()
        viewModel.onSaveExercise()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.exerciseEditor.nameConflict)
        assertTrue(custom.created.isEmpty())
        viewModel.onEditorNameChanged("Distinct name")
        assertFalse(viewModel.state.value.exerciseEditor.nameConflict)
        assertNull(viewModel.state.value.exerciseEditor.error)
        assertEquals(
            suggestedProfile().profile.involvements,
            viewModel.state.value.exerciseEditor.involvements
        )
    }

    private fun suggestedProfile(
        capability: ExerciseLoadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE
    ) = CatalogExerciseProfile(
        catalogId = "bench-id",
        canonicalName = "Canonical Bench",
        displayName = "Bench",
        aliases = listOf("Langhantel-Bankdrücken"),
        profile = ExerciseProfile(
            setOf(EquipmentTag.DUMBBELL),
            MovementPattern.HORIZONTAL_PUSH,
            mapOf(MuscleGroup.CHEST_UPPER to 0.83, MuscleGroup.TRICEPS to 0.17),
            capability,
            true
        )
    )

    private class ProfileCatalog(
        private val snapshot: List<CatalogExerciseProfile>,
        private val gate: CompletableDeferred<List<CatalogExerciseProfile>>? = null,
        var failure: Exception? = null
    ) : ExerciseCatalog {
        var reads = 0
        override suspend fun all(): List<Exercise> = snapshot.map { candidate ->
            Exercise(
                id = candidate.catalogId,
                name = candidate.displayName,
                requiredEquipment = candidate.profile.equipment,
                primaryMuscles = emptySet(),
                movementPattern = candidate.profile.movementPattern,
                isUnilateral = candidate.profile.isUnilateral,
                loadCapability = candidate.profile.loadCapability,
                involvements = candidate.profile.involvements
            )
        }
        override suspend fun profileCandidates(): List<CatalogExerciseProfile> {
            reads++
            failure?.let { throw it }
            return gate?.await() ?: snapshot
        }
    }

    private fun viewModel(
        equipment: FakeEquipmentRepository = FakeEquipmentRepository(),
        selection: FakeSelectionRepository = FakeSelectionRepository(emptySet()),
        overrides: FakeExerciseOverrideRepository = FakeExerciseOverrideRepository(),
        custom: FakeCustomExerciseRepository = FakeCustomExerciseRepository(),
        records: FakePersonalRecordRepository = FakePersonalRecordRepository(),
        catalog: ExerciseCatalog? = null
    ) = EquipmentProfilerViewModel(
        equipmentRepository = equipment,
        selectionRepository = selection,
        exerciseCatalog = catalog ?: FakeExerciseCatalog(overrides, custom),
        exerciseOverrideRepository = overrides,
        customExerciseRepository = custom,
        personalRecordRepository = records
    )

    private class FakePersonalRecordRepository(private val setFailure: String? = null) :
        PersonalRecordRepository {
        private val state = MutableStateFlow<List<PersonalRecord>>(emptyList())

        override fun observe(): Flow<List<PersonalRecord>> = state.asStateFlow()

        override suspend fun set(record: PersonalRecord) {
            setFailure?.let { throw IllegalStateException(it) }
            state.value = state.value.filterNot { it.exerciseId == record.exerciseId } + record
        }

        override suspend fun clear(exerciseId: String) {
            state.value = state.value.filterNot { it.exerciseId == exerciseId }
        }
    }

    private class FakeSelectionRepository(initial: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        var stored: Set<EquipmentTag> = initial

        override suspend fun selected(): Set<EquipmentTag> = stored

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(stored)

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            stored = tags
        }
    }

    private class FakeEquipmentRepository(
        private val addFailure: String? = null,
        private val renameFailure: String? = null
    ) : EquipmentRepository {
        private val state = MutableStateFlow(
            listOf(
                Equipment(EquipmentTag.BARBELL, "Barbell", isBuiltIn = true),
                Equipment(EquipmentTag.DUMBBELL, "Dumbbells", isBuiltIn = true)
            )
        )

        override fun observeAll(): Flow<List<Equipment>> = state.asStateFlow()

        override suspend fun all(): List<Equipment> = state.value

        override suspend fun add(name: String): Equipment {
            addFailure?.let { throw IllegalStateException(it) }
            val created = Equipment(EquipmentTag(name.uppercase()), name, isBuiltIn = false)
            state.value = state.value + created
            return created
        }

        override suspend fun remove(id: EquipmentTag) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun rename(
            id: EquipmentTag,
            name: String,
            maxWeightKg: Double?
        ): Equipment {
            renameFailure?.let { throw IllegalStateException(it) }
            val renamed = Equipment(id, name, isBuiltIn = false, maxWeightKg = maxWeightKg)
            state.value = state.value.map { if (it.id == id) renamed else it }
            return renamed
        }

        override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) {
            state.value = state.value.map {
                if (it.id == id) it.copy(maxWeightKg = maxWeightKg) else it
            }
        }
    }

    private data class StoredOverride(
        val name: String?,
        val equipment: Set<EquipmentTag>,
        val pattern: MovementPattern?,
        val unilateral: Boolean?,
        val involvements: Map<MuscleGroup, Double>
    )

    private class FakeExerciseOverrideRepository : ExerciseOverrideRepository {
        val overrides = mutableMapOf<String, StoredOverride>()

        override suspend fun update(
            exerciseId: String,
            name: String?,
            requiredEquipment: Set<EquipmentTag>,
            movementPattern: MovementPattern?,
            unilateral: Boolean?,
            loadCapability: ExerciseLoadCapability?,
            involvements: Map<MuscleGroup, Double>
        ) {
            overrides[exerciseId] = StoredOverride(
                name = name,
                equipment = requiredEquipment,
                pattern = movementPattern,
                unilateral = unilateral,
                involvements = involvements
            )
        }

        override suspend fun reset(exerciseId: String) {
            overrides.remove(exerciseId)
        }
    }

    private class FakeCustomExerciseRepository(
        private val deleteFailure: String? = null,
        private val addFailure: CustomExerciseException? = null
    ) : CustomExerciseRepository {
        val created = mutableListOf<Exercise>()

        override suspend fun add(
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ): Exercise {
            addFailure?.let { throw it }
            val exercise = Exercise(
                id = "user-" + name.lowercase().replace(' ', '-'),
                name = name,
                requiredEquipment = requiredEquipment,
                primaryMuscles = involvements.filterValues { it >= 0.7 }.keys,
                secondaryMuscles = involvements.filterValues { it < 0.7 }.keys,
                movementPattern = movementPattern,
                isCustom = true,
                isUnilateral = isUnilateral,
                loadCapability = loadCapability,
                involvements = involvements
            )
            created += exercise
            return exercise
        }

        override suspend fun update(
            id: String,
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ) = Unit

        override suspend fun delete(id: String) {
            deleteFailure?.let { throw CustomExerciseException(it) }
        }
    }

    private class FakeExerciseCatalog(
        private val overrides: FakeExerciseOverrideRepository,
        private val custom: FakeCustomExerciseRepository
    ) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> {
            val override = overrides.overrides["back-squat"]
            return listOf(
                Exercise(
                    id = "back-squat",
                    name = override?.name ?: "Back Squat",
                    requiredEquipment = override?.equipment ?: setOf(EquipmentTag.BARBELL),
                    primaryMuscles = override?.involvements?.filterValues { it >= 0.7 }?.keys
                        ?: setOf(MuscleGroup.QUADS),
                    secondaryMuscles = override?.involvements?.filterValues { it < 0.7 }?.keys
                        ?: setOf(MuscleGroup.GLUTES),
                    movementPattern = override?.pattern ?: MovementPattern.SQUAT,
                    isUnilateral = override?.unilateral ?: false,
                    involvements = override?.involvements ?: emptyMap()
                )
            ) + custom.created
        }
    }
}
