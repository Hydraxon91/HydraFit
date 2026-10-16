package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.PRESCRIPTION_FORMAT
import com.hydrafit.app.core.domain.transfer.PRESCRIPTION_FORMAT_VERSION
import com.hydrafit.app.core.domain.transfer.PrescriptionDocument
import com.hydrafit.app.core.domain.transfer.PrescriptionEntryRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionExerciseRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionIdentityChoice
import com.hydrafit.app.core.domain.transfer.PrescriptionImportChoices
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportException
import com.hydrafit.app.core.domain.transfer.PrescriptionImportIssue
import com.hydrafit.app.core.domain.transfer.PrescriptionImportPreview
import com.hydrafit.app.core.domain.transfer.PrescriptionImportProblem
import com.hydrafit.app.core.domain.transfer.PrescriptionRoutineRecord
import com.hydrafit.app.core.domain.transfer.PrescriptionWorkoutRecord
import com.hydrafit.app.core.domain.transfer.PreviewPrescriptionImport
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The transactional side of prescription import: new routines get fresh identities, a definition is
 * created only when its id is free, and a refused commit leaves no partial import.
 */
class SqlDelightPrescriptionImportRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightPrescriptionImportRepository
    private lateinit var routines: SqlDelightRoutineTemplateRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightPrescriptionImportRepository(database, TimeProvider { 1_000L })
        routines = SqlDelightRoutineTemplateRepository(database)
    }

    @AfterTest
    fun tearDown() = driver.close()

    private fun document(
        customExercises: List<PrescriptionExerciseRecord> = emptyList(),
        exerciseId: String = "back-squat",
        includesWeights: Boolean = true
    ) = PrescriptionDocument(
        format = PRESCRIPTION_FORMAT,
        formatVersion = PRESCRIPTION_FORMAT_VERSION,
        appVersion = "t",
        exportedAtMillis = 1L,
        includesWeights = includesWeights,
        routines = listOf(
            PrescriptionRoutineRecord(
                name = "Shared plan",
                workouts = listOf(
                    PrescriptionWorkoutRecord(
                        "Day 1",
                        entries = listOf(
                            PrescriptionEntryRecord(
                                exerciseId = exerciseId,
                                exerciseName = exerciseId,
                                sets = 4,
                                reps = 6,
                                weightKg = 80.0.takeIf { includesWeights },
                                loadKind = "EXTERNAL"
                            )
                        )
                    ),
                    PrescriptionWorkoutRecord(
                        "Day 2",
                        entries = listOf(
                            PrescriptionEntryRecord(
                                exerciseId = exerciseId,
                                exerciseName = exerciseId,
                                sets = 3,
                                reps = 8
                            )
                        )
                    )
                )
            )
        ),
        customExercises = customExercises
    )

    private suspend fun preview(
        document: PrescriptionDocument,
        choices: PrescriptionImportChoices = PrescriptionImportChoices()
    ) = PreviewPrescriptionImport.build(
        document = document,
        choices = choices,
        catalog = SqlDelightExerciseCatalog(database).all(),
        selectedEquipment = setOf(EquipmentTag.BARBELL, EquipmentTag.DUMBBELL)
    )

    @Test
    fun appendsAnImportableRoutineWithFreshIdentitiesAndNoScheduleChange() = runBlocking {
        val plan = preview(document())
        assertTrue(plan.canImport, plan.problems.toString())
        val beforeSchedule = database.trainingScheduleQueries.selectAllActivations().executeAsList()

        val result = repository.importPrescriptions(plan)

        assertEquals(1, result.importedRoutines)
        assertEquals(0, result.createdExercises)
        val templateId = database.routineTemplateQueries.selectAllTemplates()
            .executeAsOne().id
        val stored = routines.get(templateId)
        assertEquals("Shared plan", stored?.name)
        assertEquals(listOf("Day 1", "Day 2"), stored?.workouts?.map { it.name })
        assertEquals(listOf(0, 1), stored?.workouts?.map { it.position })
        val entrySets = stored?.workouts?.flatMap { it.entries.orEmpty() }?.map { it.sets }
        assertEquals(listOf(4, 3), entrySets)
        assertTrue(stored!!.id != 0L)
        // Import never touches the schedule or creates accepted-plan history.
        assertEquals(
            beforeSchedule,
            database.trainingScheduleQueries.selectAllActivations().executeAsList()
        )
        assertEquals(
            0L,
            database.planHistoryQueries.selectAllPlans().executeAsList().size.toLong()
        )
    }

    @Test
    fun createsAnApprovedDefinitionAndItsSearchProjection() = runBlocking {
        val definition = PrescriptionExerciseRecord(
            id = "user-shared-lift",
            name = "Shared Lift",
            requiredEquipment = listOf("DUMBBELL"),
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = false,
            loadCapability = "EXTERNAL",
            involvements = mapOf("CHEST_UPPER" to 0.7)
        )
        val plan = preview(
            document(customExercises = listOf(definition), exerciseId = definition.id),
            PrescriptionImportChoices(
                mapOf(definition.id to PrescriptionIdentityChoice.Create)
            )
        )
        assertTrue(plan.canImport, plan.problems.toString())

        val result = repository.importPrescriptions(plan)

        assertEquals(1, result.createdExercises)
        val row = database.exerciseQueries.selectById(definition.id).executeAsOne()
        assertEquals("Shared Lift", row.name)
        assertEquals("DUMBBELL", row.requiredEquipment)
        assertEquals("CHEST_UPPER:0.7", row.involvements)
        // The searchable projection follows, so the new exercise is findable immediately.
        assertEquals(
            "shared lift",
            database.exerciseQueries.selectById(definition.id).executeAsOne().searchName
        )
    }

    @Test
    fun anIdentityThatAppearedSinceThePreviewRefusesTheWholeCommit() = runBlocking {
        val definition = PrescriptionExerciseRecord(
            id = "user-shared-lift",
            name = "Shared Lift",
            requiredEquipment = listOf("DUMBBELL"),
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = false,
            loadCapability = "EXTERNAL"
        )
        val plan = preview(
            document(customExercises = listOf(definition), exerciseId = definition.id),
            PrescriptionImportChoices(
                mapOf(definition.id to PrescriptionIdentityChoice.Create)
            )
        )
        assertTrue(plan.canImport, plan.problems.toString())
        // A local row claims that identity after the preview was approved.
        database.exerciseQueries.insertCustom(
            id = definition.id,
            name = "Shared Lift",
            requiredEquipment = "DUMBBELL",
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = null
        )

        val failure = assertFailsWith<PrescriptionImportException> {
            repository.importPrescriptions(plan)
        }

        assertEquals(PrescriptionImportCommitFailure.IDENTITY_ALREADY_EXISTS, failure.failure)
        assertEquals(
            0,
            database.routineTemplateQueries.selectAllTemplates().executeAsList().size
        )
    }

    @Test
    fun aPreviewWithNoRoutineIsRefused() {
        val empty = PrescriptionImportPreview(
            document = document(),
            identities = emptyList(),
            routines = emptyList(),
            customExercisesToCreate = emptyList(),
            problems = emptyList()
        )
        val failure = assertFailsWith<PrescriptionImportException> {
            runBlocking { repository.importPrescriptions(empty) }
        }
        assertEquals(PrescriptionImportCommitFailure.NOTHING_TO_IMPORT, failure.failure)
    }

    @Test
    fun aPreviewThatStillCarriesProblemsIsRefusedAndWritesNothing() = runBlocking {
        val plan = preview(document())
        assertTrue(plan.canImport, plan.problems.toString())
        // A caller that ignores canImport must not be able to import this partial plan.
        val unresolved = plan.copy(
            problems = listOf(
                PrescriptionImportProblem(PrescriptionImportIssue.INVALID_ROUTINE)
            )
        )

        val failure = assertFailsWith<PrescriptionImportException> {
            repository.importPrescriptions(unresolved)
        }

        assertEquals(PrescriptionImportCommitFailure.PREVIEW_NOT_IMPORTABLE, failure.failure)
        assertEquals(
            0,
            database.routineTemplateQueries.selectAllTemplates().executeAsList().size
        )
    }
}
