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
import com.hydrafit.app.core.userdata.equipment.ExerciseEquipmentRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseMuscleRepository
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
    fun openingABuiltInEditorSeedsCurrentOverridesAndSavesThem() = runTest(dispatcher) {
        val equipmentEdits = FakeExerciseEquipmentRepository()
        val muscleEdits = FakeExerciseMuscleRepository()
        val viewModel = viewModel(exerciseEquipment = equipmentEdits, exerciseMuscle = muscleEdits)
        advanceUntilIdle()

        viewModel.onEditExercise("back-squat")
        assertEquals(setOf(EquipmentTag.BARBELL), viewModel.state.value.exerciseEditor.equipment)
        assertEquals(setOf(MuscleGroup.QUADS), viewModel.state.value.exerciseEditor.primary)

        viewModel.onEditorEquipmentToggled(EquipmentTag.BENCH)
        viewModel.onEditorMuscleToggled(MuscleGroup.CHEST, primary = true)
        viewModel.onSaveExercise()
        advanceUntilIdle()

        assertEquals(
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            equipmentEdits.overrides["back-squat"]
        )
        assertEquals(
            setOf(MuscleGroup.QUADS, MuscleGroup.CHEST),
            muscleEdits.primary["back-squat"]
        )
        assertNull(viewModel.state.value.exerciseEditor.exerciseId)
    }

    @Test
    fun resetClearsBothOverridesForABuiltIn() = runTest(dispatcher) {
        val equipmentEdits = FakeExerciseEquipmentRepository(
            mutableMapOf("back-squat" to setOf(EquipmentTag.DUMBBELL))
        )
        val muscleEdits = FakeExerciseMuscleRepository()
        val viewModel = viewModel(exerciseEquipment = equipmentEdits, exerciseMuscle = muscleEdits)
        advanceUntilIdle()

        viewModel.onEditExercise("back-squat")
        viewModel.onResetExercise()
        advanceUntilIdle()

        assertTrue(equipmentEdits.overrides.isEmpty())
        assertTrue(muscleEdits.primary.isEmpty())
    }

    @Test
    fun aMuscleIsEitherPrimaryOrSecondaryNeverBoth() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onEditExercise("back-squat")

        // GLUTES is seeded secondary; promoting to primary removes it from secondary.
        viewModel.onEditorMuscleToggled(MuscleGroup.GLUTES, primary = true)

        val editor = viewModel.state.value.exerciseEditor
        assertTrue(MuscleGroup.GLUTES in editor.primary)
        assertFalse(MuscleGroup.GLUTES in editor.secondary)
    }

    @Test
    fun createsACustomExerciseThroughTheEditor() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository()
        val viewModel = viewModel(custom = custom)
        advanceUntilIdle()

        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Trap Bar Deadlift")
        viewModel.onEditorPatternChanged(MovementPattern.HINGE)
        viewModel.onEditorMuscleToggled(MuscleGroup.BACK, primary = true)
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
        viewModel.onEditorMuscleToggled(MuscleGroup.CORE, primary = true)

        assertTrue(viewModel.state.value.exerciseEditor.canSave)
    }

    @Test
    fun surfacesDeleteFailureFromTheRepository() = runTest(dispatcher) {
        val custom = FakeCustomExerciseRepository(deleteFailure = "This exercise has logged sets")
        val viewModel = viewModel(custom = custom)
        advanceUntilIdle()
        viewModel.onNewCustomExercise()
        viewModel.onEditorNameChanged("Disposable")
        viewModel.onEditorMuscleToggled(MuscleGroup.CORE, primary = true)
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
        exerciseEquipment: FakeExerciseEquipmentRepository = FakeExerciseEquipmentRepository(),
        exerciseMuscle: FakeExerciseMuscleRepository = FakeExerciseMuscleRepository(),
        custom: FakeCustomExerciseRepository = FakeCustomExerciseRepository()
    ) = EquipmentProfilerViewModel(
        equipmentRepository = equipment,
        selectionRepository = selection,
        exerciseCatalog = FakeExerciseCatalog(exerciseEquipment, exerciseMuscle, custom),
        exerciseEquipmentRepository = exerciseEquipment,
        exerciseMuscleRepository = exerciseMuscle,
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
    }

    private class FakeExerciseEquipmentRepository(
        val overrides: MutableMap<String, Set<EquipmentTag>> = mutableMapOf()
    ) : ExerciseEquipmentRepository {
        override suspend fun update(exerciseId: String, equipment: Set<EquipmentTag>) {
            overrides[exerciseId] = equipment
        }

        override suspend fun reset(exerciseId: String) {
            overrides.remove(exerciseId)
        }
    }

    private class FakeExerciseMuscleRepository : ExerciseMuscleRepository {
        val primary = mutableMapOf<String, Set<MuscleGroup>>()
        val secondary = mutableMapOf<String, Set<MuscleGroup>>()

        override suspend fun update(
            exerciseId: String,
            primaryMuscles: Set<MuscleGroup>,
            secondaryMuscles: Set<MuscleGroup>
        ) {
            primary[exerciseId] = primaryMuscles
            secondary[exerciseId] = secondaryMuscles
        }

        override suspend fun reset(exerciseId: String) {
            primary.remove(exerciseId)
            secondary.remove(exerciseId)
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
            movementPattern: MovementPattern
        ): Exercise {
            val exercise = Exercise(
                id = "user-" + name.lowercase().replace(' ', '-'),
                name = name,
                requiredEquipment = requiredEquipment,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = secondaryMuscles,
                movementPattern = movementPattern,
                isCustom = true
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
            movementPattern: MovementPattern
        ) = Unit

        override suspend fun delete(id: String) {
            deleteFailure?.let { throw CustomExerciseException(it) }
        }
    }

    private class FakeExerciseCatalog(
        private val equipmentEdits: FakeExerciseEquipmentRepository,
        private val muscleEdits: FakeExerciseMuscleRepository,
        private val custom: FakeCustomExerciseRepository
    ) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = equipmentEdits.overrides["back-squat"]
                    ?: setOf(EquipmentTag.BARBELL),
                primaryMuscles = muscleEdits.primary["back-squat"] ?: setOf(MuscleGroup.QUADS),
                secondaryMuscles = muscleEdits.secondary["back-squat"] ?: setOf(MuscleGroup.GLUTES),
                movementPattern = MovementPattern.SQUAT
            )
        ) + custom.created
    }
}
