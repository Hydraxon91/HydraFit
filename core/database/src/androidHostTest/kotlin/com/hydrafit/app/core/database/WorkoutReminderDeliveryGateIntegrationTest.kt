package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The reminder delivery gate against the real SQLDelight repositories and an in-memory database:
 * a real mutator invalidates the token a post was decided from, the gate linearizes a real write
 * against a suspended posting section, and a gated restore clears device-local delivery state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutReminderDeliveryGateIntegrationTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun aRealScheduleMutationInvalidatesTheSnapshotToken() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val schedule = SqlDelightWorkoutScheduleRepository(database, gate)
        val token = gate.snapshot { schedule.scheduleState() }.token

        assertNotNull(gate.ifCurrent(token) { })

        schedule.setScheduleState(WorkoutScheduleState(activeActivationId = 42L))

        assertNull(gate.ifCurrent(token) { })
    }

    @Test
    fun aRealPreferenceMutationInvalidatesTheSnapshotToken() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val preferences = SqlDelightWorkoutReminderPreferenceRepository(database, gate)
        val token = gate.snapshot { preferences.get() }.token

        preferences.setEnabled(true)

        assertNull(gate.ifCurrent(token) { })
    }

    @Test
    fun theGateBlocksARealMutatorUntilThePostingSectionFinishes() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val schedule = SqlDelightWorkoutScheduleRepository(database, gate)
        val actionStarted = CompletableDeferred<Unit>()
        val releaseAction = CompletableDeferred<Unit>()
        val token = gate.snapshot { schedule.scheduleState() }.token

        launch {
            gate.ifCurrent(token) {
                actionStarted.complete(Unit)
                releaseAction.await()
            }
        }
        runCurrent()
        assertTrue(actionStarted.isCompleted)

        launch { schedule.setScheduleState(WorkoutScheduleState(activeActivationId = 7L)) }
        runCurrent()
        // The writer cannot commit while the posting section holds the gate.
        assertNull(schedule.scheduleState().activeActivationId)

        releaseAction.complete(Unit)
        advanceUntilIdle()
        assertEquals(7L, schedule.scheduleState().activeActivationId)
    }

    @Test
    fun aFailedRealMutationInvalidatesAndReleasesTheGate() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val schedule = SqlDelightWorkoutScheduleRepository(database, gate)
        val token = gate.snapshot { schedule.scheduleState() }.token

        assertFailsWith<ScheduleException> {
            schedule.resolveOccurrence(
                occurrenceId = 999L,
                expectedRevision = 1,
                status = OccurrenceStatus.SKIPPED,
                resolvedAtMillis = 0L,
                entries = emptyList()
            )
        }

        assertNull(gate.ifCurrent(token) { })
        // The gate remains usable, and a fresh mutation invalidates its own token.
        val fresh = gate.snapshot { schedule.scheduleState() }.token
        schedule.setScheduleState(WorkoutScheduleState())
        assertNull(gate.ifCurrent(fresh) { })
    }

    @Test
    fun restoringInvalidatesTheSnapshotAndClearsTheDeliveryLedger() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val deliveries = SqlDelightReminderDeliveryRepository(database)
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog(), gate)
        val exported = backup.export(appVersion = "integration", exportedAtMillis = 1L)

        assertTrue(deliveries.claim(5L, 20_000L, 0L))
        val token = gate.snapshot { deliveries.isDelivered(5L, 20_000L) }.token

        backup.restore(exported)

        // The gated restore invalidates a snapshot taken before it and clears device-local state.
        assertNull(gate.ifCurrent(token) { })
        assertFalse(deliveries.isDelivered(5L, 20_000L))
    }

    @Test
    fun resolvingARealOccurrenceInvalidatesTheSnapshotToken() = runTest {
        val gate = WorkoutReminderDeliveryGate()
        val schedule = SqlDelightWorkoutScheduleRepository(database, gate)
        val activationId = schedule.acceptAndActivate(
            acceptedPlan = null,
            activation = TrainingActivation(
                id = 0L,
                name = "Block",
                createdAtMillis = 0L,
                startEpochDay = 0L,
                mode = ScheduleMode.SEQUENCE,
                weekdays = emptySet(),
                status = ActivationStatus.ACTIVE,
                workouts = listOf(ActivationWorkout(id = 0L, position = 0, name = "Lower A"))
            ),
            scheduledEpochDays = listOf(null),
            replaceActive = false
        )
        val occurrence = database.trainingScheduleQueries
            .selectOccurrencesForActivation(activationId).executeAsList().single()
        val token = gate.snapshot { schedule.occurrences(activationId) }.token

        schedule.resolveOccurrence(
            occurrenceId = occurrence.id,
            expectedRevision = occurrence.revision.toInt(),
            status = OccurrenceStatus.SKIPPED,
            resolvedAtMillis = 0L,
            entries = emptyList()
        )

        assertNull(gate.ifCurrent(token) { })
    }

    @Test
    fun theRealLedgerClaimsADayOnlyOnce() = runTest {
        val deliveries = SqlDelightReminderDeliveryRepository(database)

        assertTrue(deliveries.claim(1L, 100L, 0L))
        assertFalse(deliveries.claim(1L, 100L, 0L))
        assertTrue(deliveries.isDelivered(1L, 100L))

        deliveries.release(1L, 100L)
        assertFalse(deliveries.isDelivered(1L, 100L))
    }
}
