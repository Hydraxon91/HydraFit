package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun togglingPersistsSelection() = runTest(dispatcher) {
        val selection = FakeSelectionRepository(emptySet())
        val viewModel = viewModel(selection = selection)
        advanceUntilIdle()

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()

        assertEquals(setOf(EquipmentTag.DUMBBELL), selection.stored)
    }

    @Test
    fun managesCustomEquipmentRenameAndDelete() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val viewModel = viewModel(equipment = equipment)
        advanceUntilIdle()
        val customId = equipment.add("Trap Bar").id
        advanceUntilIdle()

        viewModel.onManageEquipment(customId)
        viewModel.onRenameEquipmentNameChanged("Hex Bar")
        viewModel.onSaveEquipmentRenamed()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.equipment.any { it.name == "Hex Bar" })
        assertFalse(viewModel.state.value.equipment.any { it.id == customId })
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
        assertEquals(setOf(MuscleGroup.QUADS, MuscleGroup.CHEST), stored.primary)
        assertNull(viewModel.state.value.exerciseEditor.exerciseId)
    }

    @Test
    fun seedsAndSavesTheUnilateralFlag() = runTest(dispatcher) {
        val overrides = FakeExerciseOverrideRepository()
        overrides.update(
            "back-squat",
            null,
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.GLUTES),
            MovementPattern.SQUAT,
            unilateral = true
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
            "back-squat",
            "Renamed",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS),
            emptySet(),
            MovementPattern.SQUAT
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

    private fun viewModel(
        equipment: FakeEquipmentRepository = FakeEquipmentRepository(),
        selection: FakeSelectionRepository = FakeSelectionRepository(emptySet()),
        overrides: FakeExerciseOverrideRepository = FakeExerciseOverrideRepository(),
        custom: FakeCustomExerciseRepository = FakeCustomExerciseRepository()
    ) = EquipmentProfilerViewModel(
        equipmentRepository = equipment,
        selectionRepository = selection,
        exerciseCatalog = FakeExerciseCatalog(overrides, custom),
        exerciseOverrideRepository = overrides,
        customExerciseRepository = custom
    )

    private class FakeSelectionRepository(initial: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        var stored: Set<EquipmentTag> = initial

        override suspend fun selected(): Set<EquipmentTag> = stored

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(stored)

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            stored = tags
        }
    }

    private class FakeEquipmentRepository : EquipmentRepository {
        private val state = MutableStateFlow(
            listOf(
                Equipment(EquipmentTag.BARBELL, "Barbell", isBuiltIn = true),
                Equipment(EquipmentTag.DUMBBELL, "Dumbbells", isBuiltIn = true)
            )
        )

        override fun observeAll(): Flow<List<Equipment>> = state.asStateFlow()

        override suspend fun all(): List<Equipment> = state.value

        override suspend fun add(name: String): Equipment {
            val created = Equipment(EquipmentTag(name.uppercase()), name, isBuiltIn = false)
            state.value = state.value + created
            return created
        }

        override suspend fun remove(id: EquipmentTag) {
            state.value = state.value.filterNot { it.id == id }
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
        val primary: Set<MuscleGroup>,
        val secondary: Set<MuscleGroup>,
        val pattern: MovementPattern?,
        val unilateral: Boolean?,
        val involvements: Map<MuscleGroup, Double>?
    )

    private class FakeExerciseOverrideRepository : ExerciseOverrideRepository {
        val overrides = mutableMapOf<String, StoredOverride>()

        override suspend fun update(
            exerciseId: String,
            name: String?,
            requiredEquipment: Set<EquipmentTag>,
            primaryMuscles: Set<MuscleGroup>,
            secondaryMuscles: Set<MuscleGroup>,
            movementPattern: MovementPattern?,
            unilateral: Boolean?,
            involvements: Map<MuscleGroup, Double>?
        ) {
            overrides[exerciseId] = StoredOverride(
                name = name,
                equipment = requiredEquipment,
                primary = primaryMuscles,
                secondary = secondaryMuscles,
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
            primaryMuscles: Set<MuscleGroup>,
            secondaryMuscles: Set<MuscleGroup>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            involvements: Map<MuscleGroup, Double>
        ): Exercise {
            val exercise = Exercise(
                id = "user-" + name.lowercase().replace(' ', '-'),
                name = name,
                requiredEquipment = requiredEquipment,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = secondaryMuscles,
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
            primaryMuscles: Set<MuscleGroup>,
            secondaryMuscles: Set<MuscleGroup>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            involvements: Map<MuscleGroup, Double>
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
                    primaryMuscles = override?.primary ?: setOf(MuscleGroup.QUADS),
                    secondaryMuscles = override?.secondary ?: setOf(MuscleGroup.GLUTES),
                    movementPattern = override?.pattern ?: MovementPattern.SQUAT,
                    isUnilateral = override?.unilateral ?: false
                )
            ) + custom.created
        }
    }
}
