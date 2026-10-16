package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.ExportPrescriptionsUseCase
import com.hydrafit.app.core.domain.transfer.PRESCRIPTION_FORMAT
import com.hydrafit.app.core.domain.transfer.PRESCRIPTION_FORMAT_VERSION
import com.hydrafit.app.core.domain.transfer.PrescriptionDocument
import com.hydrafit.app.core.domain.transfer.PrescriptionEntryRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionExerciseRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionIdentityChoice
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportException
import com.hydrafit.app.core.domain.transfer.PrescriptionImportPreview
import com.hydrafit.app.core.domain.transfer.PrescriptionImportRepository
import com.hydrafit.app.core.domain.transfer.PrescriptionImportResult
import com.hydrafit.app.core.domain.transfer.PrescriptionJson
import com.hydrafit.app.core.domain.transfer.PrescriptionResolution
import com.hydrafit.app.core.domain.transfer.PrescriptionRoutineRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionWorkoutRecord
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PrescriptionShareViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val catalogExercises = listOf(
        Exercise(
            id = "BENCH",
            name = "Bench Press",
            requiredEquipment = emptySet(),
            primaryMuscles = emptySet()
        )
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        fileStore: BackupFileStore = FakeFileStore(),
        importRepository: PrescriptionImportRepository = FakeImportRepository(),
        routines: RoutineTemplateRepository = FakeRoutineRepository()
    ): PrescriptionShareViewModel = PrescriptionShareViewModel(
        exportPrescriptions = ExportPrescriptionsUseCase(routines, FakeCatalog(catalogExercises)),
        importRepository = importRepository,
        fileStore = fileStore,
        exerciseCatalog = FakeCatalog(catalogExercises),
        equipmentSelection = FakeEquipmentSelection(),
        appVersionProvider = FakeAppVersionProvider,
        timeProvider = TimeProvider { 42L }
    )

    @Test
    fun exportWritesJsonWithWeightsOffByDefault() = runTest {
        val store = FakeFileStore()
        val vm = viewModel(fileStore = store, routines = FakeRoutineRepository(listOf(template())))

        vm.onExportRequested(template())
        assertEquals(false, vm.state.value.export?.includeWeights)

        vm.exportRoutine("content://export")
        advanceUntilIdle()

        assertEquals(PrescriptionExportStatus.Saved, vm.state.value.export?.status)
        val written = assertNotNull(store.written["content://export"])
        val decoded = PrescriptionJson.decode(written)
        assertFalse(decoded.includesWeights)
        assertEquals("Leg Day", decoded.routines.single().name)
        val entry = decoded.routines.single().workouts.single().entries.single()
        assertEquals("BENCH", entry.exerciseId)
        assertEquals(null, entry.weightKg)
    }

    @Test
    fun exportWithWeightsChosenCarriesThePrescribedLoad() = runTest {
        val store = FakeFileStore()
        val weighted = template().let { draft ->
            draft.copy(
                workouts = draft.workouts.map { workout ->
                    workout.copy(entries = workout.entries.map { it.copy(weightKg = 80.0) })
                }
            )
        }
        val vm = viewModel(fileStore = store, routines = FakeRoutineRepository(listOf(weighted)))

        vm.onExportRequested(weighted)
        vm.onExportWeightsChanged(true)
        vm.exportRoutine("content://export")
        advanceUntilIdle()

        val decoded = PrescriptionJson.decode(assertNotNull(store.written["content://export"]))
        assertTrue(decoded.includesWeights)
        assertEquals(80.0, decoded.routines.single().workouts.single().entries.single().weightKg)
    }

    @Test
    fun importingAnExistingIdentityCanCommitAndReportsCounts() = runTest {
        val repository = FakeImportRepository(
            result = PrescriptionImportResult(importedRoutines = 2, createdExercises = 1)
        )
        val vm = viewModel(
            fileStore = FakeFileStore(readText = PrescriptionJson.encode(document())),
            importRepository = repository
        )

        vm.onImportChosen("content://import")
        advanceUntilIdle()

        val review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertTrue(review.preview.canImport)
        assertTrue(review.preview.identities.single().resolution is PrescriptionResolution.Existing)
        assertEquals(null, review.commitFailure)

        vm.onImportConfirmed()
        advanceUntilIdle()

        assertEquals(1, repository.commits)
        assertEquals(PrescriptionImportPhase.Imported(2, 1), vm.state.value.importPhase)
    }

    @Test
    fun unknownAndUndecidedIdentitiesBlockTheImportUntilMapped() = runTest {
        val vm = viewModel(
            fileStore = FakeFileStore(readText = PrescriptionJson.encode(document(id = "CURL")))
        )

        vm.onImportChosen("content://import")
        advanceUntilIdle()

        val review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertFalse(review.preview.canImport)
        assertEquals(1, review.preview.identities.size)

        vm.onIdentityChoiceChanged("CURL", PrescriptionIdentityChoice.MapTo("BENCH"))
        advanceUntilIdle()

        val mapped = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertTrue(mapped.preview.canImport)
    }

    @Test
    fun aMalformedDocumentFailsWithMalformed() = runTest {
        val vm = viewModel(fileStore = FakeFileStore(readText = "not json"))

        vm.onImportChosen("content://import")
        advanceUntilIdle()

        assertEquals(
            PrescriptionImportPhase.Failed(PrescriptionFailure.MALFORMED),
            vm.state.value.importPhase
        )
    }

    @Test
    fun aRefusedCommitKeepsTheReview() = runTest {
        val repository = FakeImportRepository(
            failure = PrescriptionImportException(
                PrescriptionImportCommitFailure.IDENTITY_ALREADY_EXISTS,
                "already there"
            )
        )
        val vm = viewModel(
            fileStore = FakeFileStore(readText = PrescriptionJson.encode(document())),
            importRepository = repository
        )

        vm.onImportChosen("content://import")
        advanceUntilIdle()
        vm.onImportConfirmed()
        advanceUntilIdle()

        val review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertEquals(PrescriptionImportCommitFailure.IDENTITY_ALREADY_EXISTS, review.commitFailure)
        assertTrue(review.preview.canImport)
    }

    @Test
    fun anEquipmentGapIsAcceptedSeparatelyFromCreatingTheDefinition() = runTest {
        val definition = PrescriptionExerciseRecord(
            id = "user-cable-fly",
            name = "Cable Fly",
            requiredEquipment = listOf("CABLE_MACHINE"),
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = false,
            loadCapability = "EXTERNAL"
        )
        val vm = viewModel(
            fileStore = FakeFileStore(
                readText = PrescriptionJson.encode(
                    document(id = definition.id, customExercises = listOf(definition))
                )
            )
        )

        vm.onImportChosen("content://import")
        advanceUntilIdle()
        // Creating the definition leaves the cable-machine gap open.
        vm.onIdentityChoiceChanged(definition.id, PrescriptionIdentityChoice.Create)
        advanceUntilIdle()

        var review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertFalse(review.preview.canImport)
        assertTrue(review.preview.identities.single().missingEquipment.isNotEmpty())
        assertFalse(review.preview.identities.single().acceptedEquipmentGap)

        // The gap is a separate decision, so it can be accepted while the definition is created.
        vm.onEquipmentGapChanged(definition.id, true)
        advanceUntilIdle()

        review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertTrue(review.preview.canImport, review.preview.problems.toString())
        assertEquals(1, review.preview.customExercisesToCreate.size)
        assertTrue(review.preview.identities.single().acceptedEquipmentGap)

        // Undoing it blocks the import again rather than committing a silent gap.
        vm.onEquipmentGapChanged(definition.id, false)
        advanceUntilIdle()

        review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertFalse(review.preview.canImport)
    }

    @Test
    fun anAcceptedEquipmentGapIsClearedWhenTheIdentityChoiceChanges() = runTest {
        val definition = PrescriptionExerciseRecord(
            id = "user-cable-fly",
            name = "Cable Fly",
            requiredEquipment = listOf("CABLE_MACHINE"),
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = false,
            loadCapability = "EXTERNAL"
        )
        val vm = viewModel(
            fileStore = FakeFileStore(
                readText = PrescriptionJson.encode(
                    document(id = definition.id, customExercises = listOf(definition))
                )
            )
        )

        vm.onImportChosen("content://import")
        advanceUntilIdle()
        vm.onIdentityChoiceChanged(definition.id, PrescriptionIdentityChoice.Create)
        vm.onEquipmentGapChanged(definition.id, true)
        advanceUntilIdle()

        var review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertTrue(review.preview.canImport, review.preview.problems.toString())
        assertTrue(review.preview.identities.single().acceptedEquipmentGap)

        // Re-pointing the slot at a different exercise drops the acknowledgement: it belonged to the
        // exercise the user was looking at, and the new target has its own equipment list.
        vm.onIdentityChoiceChanged(definition.id, PrescriptionIdentityChoice.MapTo("BENCH"))
        advanceUntilIdle()

        review = (vm.state.value.importPhase as PrescriptionImportPhase.Reviewing).review
        assertFalse(review.preview.identities.single().acceptedEquipmentGap)
        assertTrue(review.preview.canImport, review.preview.problems.toString())
    }

    private fun template(): RoutineTemplate = RoutineTemplate(
        id = 7L,
        name = "Leg Day",
        workouts = listOf(
            RoutineWorkout(
                position = 0,
                name = "Day 1",
                entries = listOf(
                    RoutineEntry(
                        position = 0,
                        exerciseId = "BENCH",
                        sets = 3,
                        reps = 8,
                        loadKind = LoadKind.EXTERNAL
                    )
                )
            )
        )
    )

    private fun document(
        id: String = "BENCH",
        customExercises: List<PrescriptionExerciseRecord> = emptyList()
    ): PrescriptionDocument = PrescriptionDocument(
        format = PRESCRIPTION_FORMAT,
        formatVersion = PRESCRIPTION_FORMAT_VERSION,
        appVersion = "test",
        exportedAtMillis = 1L,
        includesWeights = false,
        routines = listOf(
            PrescriptionRoutineRecord(
                name = "Push Day",
                workouts = listOf(
                    PrescriptionWorkoutRecord(
                        name = "Day 1",
                        entries = listOf(
                            PrescriptionEntryRecord(
                                exerciseId = id,
                                exerciseName = "Bench Press",
                                sets = 3,
                                reps = 8,
                                loadKind = "EXTERNAL"
                            )
                        )
                    )
                )
            )
        ),
        customExercises = customExercises
    )

    private class FakeFileStore(
        private val readText: String = "",
        override val isSupported: Boolean = true
    ) : BackupFileStore {
        val written = mutableMapOf<String, String>()

        override suspend fun read(handle: String): String = readText

        override suspend fun write(handle: String, text: String) {
            written[handle] = text
        }
    }

    private class FakeImportRepository(
        private val result: PrescriptionImportResult = PrescriptionImportResult(0, 0),
        private val failure: PrescriptionImportException? = null
    ) : PrescriptionImportRepository {
        var commits = 0

        override suspend fun importPrescriptions(
            preview: PrescriptionImportPreview
        ): PrescriptionImportResult {
            commits++
            failure?.let { throw it }
            return result
        }
    }

    private class FakeCatalog(private val exercises: List<Exercise>) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = exercises
    }

    private class FakeEquipmentSelection : EquipmentSelectionRepository {
        override suspend fun selected(): Set<EquipmentTag> = emptySet()

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(emptySet())

        override suspend fun setSelected(tags: Set<EquipmentTag>) = Unit
    }

    private object FakeAppVersionProvider : AppVersionProvider {
        override val versionName: String = "test"
    }

    private class FakeRoutineRepository(
        private val templates: List<RoutineTemplate> = emptyList()
    ) : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(templates)

        override suspend fun get(id: Long): RoutineTemplate? = templates.firstOrNull { it.id == id }

        override suspend fun save(template: RoutineTemplate): Long = 0L

        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit

        override suspend fun isReferencedByActivation(id: Long): Boolean = false

        override suspend fun delete(id: Long) = Unit
    }
}
