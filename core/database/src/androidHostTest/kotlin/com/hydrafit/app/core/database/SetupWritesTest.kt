package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentProfileContext
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.setup.StaleSetupContextException
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class SetupWritesTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var reader: SqlDelightSetupRepository
    private lateinit var writer: SqlDelightSetupWriteRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        reader = SqlDelightSetupRepository(database)
        writer = SqlDelightSetupWriteRepository(database, TimeProvider { 100L })
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun saveActionsFeedExistingConsumersWithoutTrainingDataAndRoundTripBackup() = runTest {
        val save = SaveSetupStepUseCase(writer, StandardTestDispatcher(testScheduler))
        SqlDelightPersonalRecordRepository(database, TimeProvider { 0L }).set(
            PersonalRecord("back-squat", 80.0, 5)
        )
        val inputs = listOf(
            SetupStepInput.Continue(SetupStep.WELCOME),
            SetupStepInput.Units(WeightUnit.LB),
            SetupStepInput.Equipment(
                setOf(EquipmentTag("DUMBBELL")),
                mapOf(EquipmentTag("DUMBBELL") to 30.0),
                EquipmentProfileContext(1L, 0L)
            ),
            SetupStepInput.Goal(TrainingGoal.STRENGTH),
            SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            SetupStepInput.PersonalRecords(listOf(PersonalRecord("barbell-bench-press", 60.0, 5))),
            SetupStepInput.Continue(SetupStep.FIRST_WORKOUT)
        )
        inputs.forEach { save(reader.current(), it) }
        assertEquals(WeightUnit.LB, SqlDelightWeightUnitRepository(database).selectedUnit())
        assertEquals(
            TrainingGoal.STRENGTH,
            SqlDelightTrainingGoalRepository(database).selectedGoal()
        )
        assertEquals(2, SqlDelightEnginePreferenceRepository(database).selectedDaysPerWeek())
        assertEquals(
            setOf(EquipmentTag("DUMBBELL")),
            SqlDelightEquipmentSelectionRepository(database).selected()
        )
        assertEquals(
            30.0,
            database.equipmentQueries.selectById("DUMBBELL").executeAsOne().maxWeightKg
        )
        assertEquals(2, database.personalRecordQueries.selectAll().executeAsList().size)
        val retainedRecord = database.personalRecordQueries.selectAll().executeAsList()
            .first { it.exerciseId == "back-squat" }
        assertEquals(80.0, retainedRecord.weightKg)
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            SqlDelightEquipmentProfileRepository(database),
            SqlDelightEnginePreferenceRepository(database),
            SqlDelightWorkoutLogRepository(database),
            SqlDelightTrainingGoalRepository(database),
            SqlDelightPersonalRecordRepository(database, TimeProvider { 100L }),
            SqlDelightExercisePreferenceRepository(database),
            SqlDelightExerciseExclusionRepository(database)
        ).observe().first()
        assertEquals(2, sources.daysPerWeek)
        assertEquals(TrainingGoal.STRENGTH, sources.goal)
        assertEquals(setOf(EquipmentTag("DUMBBELL")), sources.availableEquipment)
        assertEquals(emptyList(), sources.loggedSets)
        val loads = BuildPlannerLoadInputsUseCase(
            SqlDelightExerciseCatalog(database),
            BuildRecentWeightsUseCase(),
            ProgressWeightsUseCase()
        )(
            sources,
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = 100L
        )
        assertEquals(
            LoadBaselineSource.MANUAL_PERSONAL_RECORD,
            loads.loadExplanations.getValue("barbell-bench-press").baseline.source
        )
        listOf("workoutSet", "workoutSession", "planHistory", "trainingActivation").forEach {
            assertEquals(0L, HistoricalDatabaseFixtures.count(driver, it))
        }
        val catalog = SqlDelightBackupCatalog()
        val backup = SqlDelightBackupRepository(database, catalog)
        val exported = BackupJson.decode(BackupJson.encode(backup.export("dev", 100L)))
        BackupValidator(catalog).validate(exported)
        backup.restore(exported)
        assertEquals(exported, backup.export("dev", 100L))
    }

    @Test
    fun staleExpectedBoundaryCannotOverwriteAnAlreadyCommittedSetting() = runTest {
        val save = SaveSetupStepUseCase(writer, StandardTestDispatcher(testScheduler))
        val initial = reader.current()
        val saved = save(initial, SetupStepInput.Units(WeightUnit.LB))
        assertFailsWith<StaleSetupContextException> {
            save(initial, SetupStepInput.Units(WeightUnit.KG))
        }
        assertEquals(saved, reader.current())
        assertEquals(WeightUnit.LB, SqlDelightWeightUnitRepository(database).selectedUnit())
    }

    @Test
    fun staleEquipmentStepAfterAwayAndBackSwitchCannotWriteEitherProfileOrAdvanceSetup() = runTest {
        val profiles = SqlDelightEquipmentProfileRepository(database)
        val other = profiles.create("Travel")
        val captured = profiles.current()
        val input = SetupStepInput.Equipment(
            setOf(EquipmentTag.BARBELL),
            profileContext = captured.context
        )
        profiles.activate(captured.context, other)
        profiles.activate(profiles.current().context, captured.active.id)
        val before = profiles.current()
        val initial = reader.current()
        assertFailsWith<com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException> {
            SaveSetupStepUseCase(writer, StandardTestDispatcher(testScheduler))(initial, input)
        }
        assertEquals(before, profiles.current())
        assertEquals(initial, reader.current())
    }

    @Test
    fun failedBoundaryUpdateRollsBackEachStepPayloadAndSkipHasNoPayload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val save = SaveSetupStepUseCase(writer, dispatcher)
        val initial = reader.current()
        driver.execute(
            null,
            "CREATE TRIGGER fail_boundary BEFORE UPDATE ON setupState " +
                "BEGIN SELECT RAISE(ABORT, 'injected'); END",
            0
        )
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val original = backup.export("dev", 0L)
        listOf(
            SetupStepInput.Units(WeightUnit.LB),
            SetupStepInput.Equipment(
                setOf(EquipmentTag("DUMBBELL")),
                mapOf(EquipmentTag("DUMBBELL") to 30.0),
                EquipmentProfileContext(1L, 0L)
            ),
            SetupStepInput.Goal(TrainingGoal.STRENGTH),
            SetupStepInput.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            SetupStepInput.PersonalRecords(listOf(PersonalRecord("back-squat", 60.0, 5)))
        ).forEach { input ->
            assertFailsWith<Exception> { save(initial, input) }
            assertEquals(original, backup.export("dev", 0L))
        }
        driver.execute(null, "DROP TRIGGER fail_boundary", 0)
        SkipSetupUseCase(writer, dispatcher)(initial)
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "personalRecord"))
        assertEquals("SKIPPED", backup.export("dev", 0L).setup.disposition)
    }

    @Test
    fun invalidCatalogIdentityAndEffectiveBodyweightOverrideRejectTheWholePrBatch() = runTest {
        val save = SaveSetupStepUseCase(writer, StandardTestDispatcher(testScheduler))
        val initial = reader.current()
        driver.execute(
            null,
            "INSERT INTO exerciseOverride(exerciseId, loadCapability) " +
                "VALUES ('back-squat', 'BODYWEIGHT_ONLY')",
            0
        )
        listOf("back-squat", "missing").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                save(
                    initial,
                    SetupStepInput.PersonalRecords(
                        listOf(
                            PersonalRecord("barbell-bench-press", 50.0, 5),
                            PersonalRecord(invalid, 60.0, 5)
                        )
                    )
                )
            }
            assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "personalRecord"))
            assertEquals(initial, reader.current())
        }
        assertFailsWith<IllegalArgumentException> {
            save(
                initial,
                SetupStepInput.Equipment(
                    setOf(EquipmentTag("missing")),
                    profileContext = EquipmentProfileContext(1L, 0L)
                )
            )
        }
        assertEquals(initial, reader.current())
    }
}
