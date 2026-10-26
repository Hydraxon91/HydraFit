package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class EquipmentProfileBackupTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var profiles: SqlDelightEquipmentProfileRepository
    private lateinit var backups: SqlDelightBackupRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        profiles = SqlDelightEquipmentProfileRepository(database)
        backups = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun multipleProfilesAndEveryLoadingFieldRoundTripThroughValidatedJson() = runTest {
        profiles.saveEquipment(
            profiles.current().context,
            setOf(EquipmentTag.BARBELL),
            mapOf(EquipmentTag.BARBELL to 120.5)
        )
        profiles.saveLoadingInventory(
            profiles.current().context,
            LoadingInventory(
                "LB",
                listOf(OwnedPlate(125, 3)),
                listOf(LoadingBar("specialty", "Specialty bar", 15_000, 500)),
                listOf(
                    LoadingDumbbell("fixed", "Fixed", listOf(1_250, 2_500)),
                    LoadingDumbbell(
                        "adjustable",
                        "Adjustable",
                        minimumTicks = 1_000,
                        maximumTicks = 20_000,
                        incrementTicks = 1_000
                    )
                )
            )
        )
        val travel = profiles.create("Travel", profiles.current().active.id)
        profiles.activate(profiles.current().context, travel)
        val expected = profiles.current()
        val file = backups.export("test", 1L)
        val validated = BackupValidator(SqlDelightBackupCatalog())
            .prepareForRestore(BackupJson.decode(BackupJson.encode(file)))
        profiles.saveEquipment(profiles.current().context, emptySet(), emptyMap())

        backups.restore(validated)

        assertEquals(expected.profiles, profiles.current().profiles)
        assertEquals(expected.context.profileId, profiles.current().context.profileId)
        assertEquals(0L, profiles.current().context.revision)
        assertEquals(file, backups.export("test", 1L))
    }

    @Test
    fun stationsAndFrozenSetAttributionRoundTripThroughBackup() = runTest {
        val context = profiles.current().context
        val stationId = profiles.saveStation(
            context,
            EquipmentStation(
                profileId = context.profileId,
                equipmentId = EquipmentTag.CABLE_MACHINE,
                name = "Cable row A",
                loadBasis = StationLoadBasis.TOTAL,
                minWeightKg = 5.0,
                maxWeightKg = 120.0,
                incrementKg = 2.5
            )
        )
        val attribution = StationAttribution(stationId, "Cable row A", StationLoadBasis.TOTAL)
        database.workoutSessionQueries.insertSession("session-1", 90L, 200L, 1L)
        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(
                exerciseId = "seated-cable-row",
                reps = 10,
                weightKg = 70.0,
                performedAtMillis = 100L,
                sessionId = "session-1",
                station = attribution
            )
        )
        val file = backups.export("test", 1L)
        val validated = BackupValidator(SqlDelightBackupCatalog())
            .prepareForRestore(BackupJson.decode(BackupJson.encode(file)))

        backups.restore(validated)

        assertEquals("Cable row A", profiles.current().active.stations.single().name)
        assertEquals(attribution, SqlDelightWorkoutLogRepository(database).all().single().station)
    }

    @Test
    fun deletingAProfileDetachesItsRecordedStationIdsSoTheBackupRemainsRestorable() = runTest {
        val original = profiles.current()
        val stationId = profiles.saveStation(
            original.context,
            EquipmentStation(
                profileId = original.active.id,
                equipmentId = EquipmentTag.CABLE_MACHINE,
                name = "Cable row A"
            )
        )
        database.workoutSessionQueries.insertSession("session-1", 90L, 200L, 1L)
        SqlDelightWorkoutLogRepository(database).add(
            WorkoutSet(
                exerciseId = "seated-cable-row",
                reps = 10,
                weightKg = 70.0,
                performedAtMillis = 100L,
                sessionId = "session-1",
                station = StationAttribution(stationId, "Cable row A", StationLoadBasis.TOTAL)
            )
        )
        val replacement = profiles.create("Travel")
        profiles.activate(repositoryContext(), replacement)
        profiles.delete(repositoryContext(), original.active.id, replacement)

        val validated = BackupValidator(SqlDelightBackupCatalog())
            .prepareForRestore(backups.export("test", 1L))

        assertEquals(
            0L,
            validated.workoutSets.single { it.exerciseId == "seated-cable-row" }.stationId
        )
        backups.restore(validated)
        assertEquals(
            StationAttribution(0L, "Cable row A", StationLoadBasis.TOTAL),
            SqlDelightWorkoutLogRepository(database).all()
                .single { it.exerciseId == "seated-cable-row" }.station
        )
    }

    private suspend fun repositoryContext() = profiles.current().context

    @Test
    fun aPreStationFormatCannotCarryStationSectionsOrSetAttribution() = runTest {
        val file = backups.export("test", 1L)
        val validator = BackupValidator(SqlDelightBackupCatalog())
        val withStation = file.copy(
            formatVersion = 24,
            equipmentStations = file.equipmentStations.ifEmpty {
                listOf(
                    com.hydrafit.app.core.domain.backup.BackupEquipmentStationRecord(
                        id = 1L,
                        profileId = file.equipmentProfiles.first().id,
                        equipmentId = EquipmentTag.CABLE_MACHINE.id,
                        name = "Cable row A",
                        loadBasis = "TOTAL"
                    )
                )
            }
        )
        assertFailsWith<BackupException> { validator.validate(withStation) }
    }

    @Test
    fun legacyRestoreReplacesProfilesWithOneAndRejectsNewSectionsOnAnOldVersion() = runTest {
        val file = backups.export("test", 1L)
        val validator = BackupValidator(SqlDelightBackupCatalog())
        assertFailsWith<BackupException> { validator.validate(file.copy(formatVersion = 13)) }
        profiles.create("Must be replaced")
        val legacy = file.copy(
            formatVersion = 13,
            equipmentProfiles = emptyList(),
            activeEquipmentProfileId = null
        )
        backups.restore(validator.prepareForRestore(legacy))
        assertEquals(1, profiles.current().profiles.size)
        assertEquals(LoadingInventory(), profiles.current().active.loadingInventory)
    }

    @Test
    fun failedProfileInsertionRollsBackTheWholeReplacement() = runTest {
        val file = backups.export("test", 1L)
        profiles.create("Keep this")
        val before = backups.export("before", 2L)
        driver.execute(
            null,
            "CREATE TRIGGER rejectProfile BEFORE INSERT ON equipmentProfile " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END",
            0
        )
        assertFailsWith<Exception> { backups.restore(file) }
        assertEquals(before, backups.export("before", 2L))
    }
}
