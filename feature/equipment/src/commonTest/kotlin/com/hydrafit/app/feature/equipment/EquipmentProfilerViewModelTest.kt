package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.CustomExerciseException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
            involvements = mapOf(MuscleGroup.CHEST to 1.0, MuscleGroup.TRICEPS to 0.4)
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
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.CHEST, 1.0)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        val stored = requireNotNull(overrides.overrides["back-squat"])
        assertEquals("Back Squat (Low Bar)", stored.name)
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), stored.equipment)
        assertEquals(1.0, stored.involvements[MuscleGroup.QUADS])
        assertEquals(1.0, stored.involvements[MuscleGroup.CHEST])
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
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.BACK, 1.0)
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
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.BACK, 1.0)
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
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.CORE, 1.0)

        assertTrue(viewModel.state.value.exerciseEditor.canSave)
    }

    @Test
    fun surfacesDeleteFailureFromTheRepository() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository(deleteFailure = "This exercise has logged sets")
        val viewModel = viewModel(custom = custom)
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Disposable")
        viewModel.onEditorMuscleInvolvementChanged(MuscleGroup.CORE, 1.0)
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

    private fun viewModel(
        equipment: FakeEquipmentRepository = FakeEquipmentRepository(),
        selection: FakeSelectionRepository = FakeSelectionRepository(emptySet()),
        overrides: FakeExerciseOverrideRepository = FakeExerciseOverrideRepository(),
        custom: FakeCustomExerciseRepository = FakeCustomExerciseRepository(),
        records: FakePersonalRecordRepository = FakePersonalRecordRepository()
    ) = EquipmentProfilerViewModel(
        equipmentRepository = equipment,
        selectionRepository = selection,
        exerciseCatalog = FakeExerciseCatalog(overrides, custom),
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

    private class FakeCustomExerciseRepository(private val deleteFailure: String? = null) :
        CustomExerciseRepository {
        val created = mutableListOf<Exercise>()

        override suspend fun add(
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean
        ): Exercise {
            val exercise = Exercise(
                id = "user-" + name.lowercase().replace(' ', '-'),
                name = name,
                requiredEquipment = requiredEquipment,
                primaryMuscles = involvements.filterValues { it >= 0.7 }.keys,
                secondaryMuscles = involvements.filterValues { it < 0.7 }.keys,
                movementPattern = movementPattern,
                isCustom = true,
                isUnilateral = isUnilateral,
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
            isUnilateral: Boolean
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
