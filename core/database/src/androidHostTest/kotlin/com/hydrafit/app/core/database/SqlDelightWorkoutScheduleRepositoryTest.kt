package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.LoadBaselineExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceEntryDraft
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementException
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutScheduleRepositoryTest {

    @Test
    fun muscleExpansionSeedingPreservesFrozenActivationAndOccurrenceProfiles() = runTest {
        SeedExerciseCatalog(database).seed()
        val seeds = SqlDelightExerciseCatalog(database).all().associateBy { it.id }
        val original = activation()
        val workout = original.workouts.first()
        val prototype = workout.entries.single()
        val profiles = MuscleExpansionCompatibility.previousProfiles
        val entries = profiles.entries.mapIndexed { index, old ->
            val seed = seeds.getValue(old.key)
            driver.execute(null, "UPDATE exercise SET involvements = ? WHERE id = ?", 2) {
                bindString(0, old.value.substringAfterLast('|'))
                bindString(1, old.key)
            }
            prototype.copy(
                position = index,
                exerciseId = seed.id,
                exerciseName = seed.name,
                movementPattern = seed.movementPattern,
                requiredEquipment = seed.requiredEquipment,
                involvements = decodeInvolvements(old.value.substringAfterLast('|')),
                isUnilateral = seed.isUnilateral,
                weightKg = null,
                loadCapability = seed.loadCapability,
                loadKind = WorkoutLoadPolicy.defaultKind(seed.loadCapability)
            )
        }
        val frozen = original.copy(workouts = listOf(workout.copy(entries = entries)))
        val id = repository.acceptAndActivate(null, frozen, listOf(null), false)
        val master = repository.getActivation(id)
        val occurrences = repository.occurrences(id)
        val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
        val before = backup.export("test", 1L)
        SeedExerciseCatalog(database).seed()
        SeedExerciseCatalog(database).seed()
        assertEquals(master, repository.getActivation(id))
        assertEquals(occurrences, repository.occurrences(id))
        assertEquals(before, backup.export("test", 1L))
    }

    @Test
    fun replacementIsAtomicAndPreservesMasterFutureOccurrenceAndEntryIdentity() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val master = repository.getActivation(id)
        val occurrences = repository.occurrences(id)
        val occurrence = occurrences.first()
        val candidate = SqlDelightExerciseCatalog(database).all().first {
            it.movementPattern == occurrence.entries.single().movementPattern &&
                it.id != occurrence.entries.single().exerciseId
        }
        seedProfileSelection(candidate.requiredEquipment)
        val replacement = occurrence.entries.single().copy(
            exerciseId = candidate.id,
            exerciseName = candidate.name,
            requiredEquipment = candidate.requiredEquipment,
            involvements = candidate.effectiveInvolvements,
            isUnilateral = candidate.isUnilateral,
            weightKg = null,
            loadCapability = candidate.loadCapability,
            loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability)
        )
        database.exerciseExclusionQueries.upsert(candidate.id, null)
        assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)
        }
        assertEquals(occurrence, repository.getOccurrence(occurrence.id))
        database.exerciseExclusionQueries.deleteById(candidate.id)
        assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(
                occurrence.id,
                occurrence.revision + 1,
                replacement,
                1L
            )
        }
        repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)
        val changed = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(replacement, changed.entries.single())
        assertEquals(occurrence.revision + 1, changed.revision)
        assertEquals(master, repository.getActivation(id))
        assertEquals(occurrences.last(), repository.getOccurrence(occurrences.last().id))
    }

    @Test
    fun replacementPersistsTheCandidatesOwnExplanation() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val candidate = SqlDelightExerciseCatalog(database).all().first {
            it.movementPattern == occurrence.entries.single().movementPattern &&
                it.id != occurrence.entries.single().exerciseId
        }
        seedProfileSelection(candidate.requiredEquipment)
        val candidateExplanation = entryExplanation()
        val replacement = occurrence.entries.single().copy(
            exerciseId = candidate.id,
            exerciseName = candidate.name,
            requiredEquipment = candidate.requiredEquipment,
            involvements = candidate.effectiveInvolvements,
            isUnilateral = candidate.isUnilateral,
            weightKg = null,
            loadCapability = candidate.loadCapability,
            loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability),
            explanation = candidateExplanation
        )

        repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)

        val changed = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(candidateExplanation, changed.entries.single().explanation)
    }

    @Test
    fun aSetLoggedAfterPreviewIncludingWarmupsPreventsReplacement() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val entry = occurrence.entries.single()
        SqlDelightWorkoutLogRepository(database).add(
            com.hydrafit.app.core.domain.workout.WorkoutSet(
                exerciseId = entry.exerciseId,
                reps = 2,
                weightKg = null,
                performedAtMillis = 1L,
                isWarmup = true,
                occurrenceId = occurrence.id,
                occurrenceEntryId = entry.id
            )
        )
        val error = assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, entry, 1L)
        }
        assertEquals(OccurrenceReplacementFailure.RECORDED_SLOT, error.reason)
        assertEquals(occurrence, repository.getOccurrence(occurrence.id))
    }

    @Test
    fun aSetLinkedToTheOccurrenceWithoutAnEntryAlsoPreventsReplacement() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val entry = occurrence.entries.single()
        // Attribution may resolve the occurrence without an entry; when the exercise matches, that
        // work still belongs to the slot, so it must not be swapped as if it were untouched.
        SqlDelightWorkoutLogRepository(database).add(
            com.hydrafit.app.core.domain.workout.WorkoutSet(
                exerciseId = entry.exerciseId,
                reps = 2,
                weightKg = null,
                performedAtMillis = 1L,
                occurrenceId = occurrence.id,
                occurrenceEntryId = null
            )
        )
        val error = assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, entry, 1L)
        }
        assertEquals(OccurrenceReplacementFailure.RECORDED_SLOT, error.reason)
    }

    @Test
    fun anOccurrenceLevelSetForAnotherExerciseLeavesTheSlotReplaceable() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val entry = occurrence.entries.single()
        val candidate = SqlDelightExerciseCatalog(database).all().first {
            it.movementPattern == entry.movementPattern && it.id != entry.exerciseId
        }
        seedProfileSelection(candidate.requiredEquipment)
        val replacement = entry.copy(
            exerciseId = candidate.id,
            exerciseName = candidate.name,
            requiredEquipment = candidate.requiredEquipment,
            involvements = candidate.effectiveInvolvements,
            isUnilateral = candidate.isUnilateral,
            weightKg = null,
            loadCapability = candidate.loadCapability,
            loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability)
        )
        // An unplanned extra exercise is not attributed to any slot, so it must not freeze them all.
        SqlDelightWorkoutLogRepository(database).add(
            com.hydrafit.app.core.domain.workout.WorkoutSet(
                exerciseId = "unplanned-extra",
                reps = 2,
                weightKg = null,
                performedAtMillis = 1L,
                occurrenceId = occurrence.id,
                occurrenceEntryId = null
            )
        )
        repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)
        assertEquals(replacement, repository.getOccurrence(occurrence.id)?.entries?.single())
    }

    @Test
    fun replacementAvailabilityFollowsTheActiveProfileAndNotTheLegacySelection() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val entry = occurrence.entries.single()
        val candidate = SqlDelightExerciseCatalog(database).all().first {
            it.movementPattern == entry.movementPattern && it.id != entry.exerciseId
        }
        val profiles = SqlDelightEquipmentProfileRepository(database)
        val home = profiles.current()
        seedProfileSelection(candidate.requiredEquipment)
        // A second, empty profile does not own the candidate's equipment, so the same
        // replacement that is valid in "home" must be rejected while "travel" is active.
        val travel = profiles.create("Travel")
        profiles.activate(profiles.current().context, travel)
        val replacement = entry.copy(
            exerciseId = candidate.id,
            exerciseName = candidate.name,
            requiredEquipment = candidate.requiredEquipment,
            involvements = candidate.effectiveInvolvements,
            isUnilateral = candidate.isUnilateral,
            weightKg = null,
            loadCapability = candidate.loadCapability,
            loadKind = WorkoutLoadPolicy.defaultKind(candidate.loadCapability)
        )
        val error = assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)
        }
        assertEquals(OccurrenceReplacementFailure.UNAVAILABLE, error.reason)

        // Switching back to the owning profile makes the replacement valid again.
        profiles.activate(profiles.current().context, home.active.id)
        repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, replacement, 1L)
        assertEquals(replacement, repository.getOccurrence(occurrence.id)?.entries?.single())
    }

    @Test
    fun anEntryLinkedSetWithoutAnOccurrenceStillPreventsReplacement() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null, null), false)
        val occurrence = repository.occurrences(id).first()
        val entry = occurrence.entries.single()
        // The entry id is the authoritative attribution: a stored row that names the entry but
        // carries no occurrence id still belongs to the slot's work and must freeze it.
        SqlDelightWorkoutLogRepository(database).add(
            com.hydrafit.app.core.domain.workout.WorkoutSet(
                exerciseId = entry.exerciseId,
                reps = 2,
                weightKg = null,
                performedAtMillis = 1L,
                occurrenceId = null,
                occurrenceEntryId = entry.id
            )
        )
        val error = assertFailsWith<OccurrenceReplacementException> {
            repository.replaceUntouchedEntry(occurrence.id, occurrence.revision, entry, 1L)
        }
        assertEquals(OccurrenceReplacementFailure.RECORDED_SLOT, error.reason)
        assertEquals(occurrence, repository.getOccurrence(occurrence.id))
    }

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWorkoutScheduleRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWorkoutScheduleRepository(database)
    }

    /**
     * Replacement availability reads the active equipment profile, not the legacy global
     * `selected_equipment` table, so a test must seed the profile the same way the app does.
     */
    private fun seedProfileSelection(tags: Set<EquipmentTag>) {
        val activeId = database.equipmentProfileQueries.selectState().executeAsOne().activeProfileId
        tags.forEach { database.equipmentProfileQueries.upsertEntry(activeId, it.id, 1L, null) }
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun scheduleStateDefaultsToLegacyFallbackWhenUnset() = runTest {
        assertEquals(WorkoutScheduleState(), repository.scheduleState())

        repository.setScheduleState(
            WorkoutScheduleState(
                activeActivationId = 5L,
                selectedOccurrenceId = 9L,
                legacyFallbackEnabled = false
            )
        )

        assertEquals(
            WorkoutScheduleState(5L, 9L, legacyFallbackEnabled = false),
            repository.scheduleState()
        )
        assertEquals(
            WorkoutScheduleState(5L, 9L, legacyFallbackEnabled = false),
            repository.observeScheduleState().first()
        )
    }

    @Test
    fun acceptAndActivateWritesActivationOccurrencesAndCursorAtomically() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        val reloaded = requireNotNull(repository.getActivation(id))
        assertEquals("Upper block", reloaded.name)
        assertEquals(ScheduleMode.WEEKDAY, reloaded.mode)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), reloaded.weekdays)
        assertEquals(ActivationStatus.ACTIVE, reloaded.status)
        assertEquals(7L, reloaded.templateId)
        assertEquals(3, reloaded.templateRevision)
        assertEquals(SplitFocus.PUSH, reloaded.workouts[0].focus)
        val slot = reloaded.workouts[0].entries.single()
        assertEquals("bench-press", slot.exerciseId)
        assertEquals("Barbell Bench Press", slot.exerciseName)
        assertEquals(setOf(EquipmentTag("barbell")), slot.requiredEquipment)
        assertEquals(mapOf(MuscleGroup.CHEST_UPPER to 0.6), slot.involvements)
        assertTrue(slot.isUnilateral.not())
        assertEquals(82.5, slot.weightKg)

        val occurrences = repository.occurrences(id)
        assertEquals(listOf(0, 1), occurrences.map { it.queuePosition })
        assertEquals(listOf(20_000L, 20_002L), occurrences.map { it.scheduledEpochDay })
        assertTrue(occurrences.all { it.status == OccurrenceStatus.PENDING })
        assertEquals(occurrences[0].id, repository.scheduleState().selectedOccurrenceId)
        assertEquals(id, repository.scheduleState().activeActivationId)

        val copiedEntry = occurrences[0].entries.single()
        assertEquals(slot.id, copiedEntry.sourceActivationEntryId)
        assertEquals("bench-press", copiedEntry.exerciseId)
        assertNull(copiedEntry.remainingDisposition)
    }

    @Test
    fun acceptAndActivatePersistsTheVolumeExplanationWithTheAcceptedPlan() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Barbell Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH
                        )
                    )
                )
            ),
            armCoverage = listOf(
                ArmMuscleCoverage(MuscleGroup.BICEPS, 4, true, 4, 1.0, null)
            ),
            volumeAttribution = PlanAttribution.DETERMINISTIC
        )

        repository.acceptAndActivate(
            acceptedPlan = accepted,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        // The Start-block path must persist the same frozen assessment as plain acceptance.
        val stored = requireNotNull(SqlDelightPlanHistoryRepository(database).latest())
        assertEquals(VolumeExplanationStatus.AVAILABLE, stored.volumeExplanationStatus)
        assertEquals(PlanAttribution.DETERMINISTIC, stored.volumeAttribution)
        assertEquals(listOf(MuscleGroup.BICEPS), stored.armCoverage.map { it.muscle })
    }

    @Test
    fun acceptAndActivateRejectsASecondBlockUnlessReplacing() = runTest {
        val first = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        assertFailsWith<ScheduleException> {
            repository.acceptAndActivate(
                acceptedPlan = null,
                activation = activation(),
                scheduledEpochDays = listOf(20_010L, 20_012L),
                replaceActive = false
            )
        }

        // The rejected write leaves the first block fully intact and writes nothing new.
        assertEquals(
            1,
            database.trainingScheduleQueries.selectAllActivations().executeAsList().size
        )
        assertEquals(
            2,
            database.trainingScheduleQueries.selectAllOccurrences().executeAsList().size
        )
        assertEquals(first, repository.scheduleState().activeActivationId)

        val second = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_010L, 20_012L),
            replaceActive = true
        )

        assertEquals(ActivationStatus.CANCELLED, repository.getActivation(first)?.status)
        assertEquals(second, repository.scheduleState().activeActivationId)
    }

    @Test
    fun resolveOccurrenceAdvancesTheCursorAndRejectsStaleRevisions() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val first = repository.occurrences(id).first()

        val state = repository.resolveOccurrence(
            occurrenceId = first.id,
            expectedRevision = first.revision,
            status = OccurrenceStatus.FINISHED,
            resolvedAtMillis = 500L,
            entries = first.entries
        )

        assertEquals(OccurrenceStatus.FINISHED, repository.getOccurrence(first.id)?.status)
        assertEquals(500L, repository.getOccurrence(first.id)?.resolvedAtMillis)
        assertEquals(first.id + 1, state.selectedOccurrenceId)

        assertFailsWith<ScheduleException> {
            repository.resolveOccurrence(
                occurrenceId = first.id,
                expectedRevision = first.revision,
                status = OccurrenceStatus.FINISHED,
                resolvedAtMillis = 600L,
                entries = first.entries
            )
        }

        // The stale attempt changes neither the occurrence nor the cursor.
        assertEquals(OccurrenceStatus.FINISHED, repository.getOccurrence(first.id)?.status)
        assertEquals(500L, repository.getOccurrence(first.id)?.resolvedAtMillis)
        assertEquals(first.id + 1, repository.scheduleState().selectedOccurrenceId)
    }

    @Test
    fun resolveOccurrencePersistsRemainingDisposition() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val entries = occurrence.entries.map {
            it.copy(remainingDisposition = RemainingDisposition.OMITTED, terminalRemainingSets = 2)
        }

        repository.resolveOccurrence(
            occurrenceId = occurrence.id,
            expectedRevision = occurrence.revision,
            status = OccurrenceStatus.FINISHED_PARTIAL,
            resolvedAtMillis = 500L,
            entries = entries
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(RemainingDisposition.OMITTED, reloaded.entries[0].remainingDisposition)
        assertEquals(2, reloaded.entries[0].terminalRemainingSets)
    }

    @Test
    fun resolveOccurrenceRejectsAnOccurrenceFromANonActiveBlock() = runTest {
        val first = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val foreign = repository.occurrences(first).first()

        repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_010L, 20_012L),
            replaceActive = true
        )

        assertFailsWith<ScheduleException> {
            repository.resolveOccurrence(
                occurrenceId = foreign.id,
                expectedRevision = foreign.revision,
                status = OccurrenceStatus.FINISHED,
                resolvedAtMillis = 500L,
                entries = foreign.entries
            )
        }
    }

    @Test
    fun resolveOccurrenceRollsBackWhenTheOccurrenceUpdateFailsMidTransaction() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val beforeState = repository.scheduleState()
        // Entry changes are applied before the occurrence update; fail the later write so the whole
        // transaction must roll back, entry edit included.
        injectFault("BEFORE UPDATE ON workoutOccurrence")

        assertFailsWith<Exception> {
            repository.resolveOccurrence(
                occurrenceId = occurrence.id,
                expectedRevision = occurrence.revision,
                status = OccurrenceStatus.FINISHED_PARTIAL,
                resolvedAtMillis = 500L,
                entries = occurrence.entries.map {
                    it.copy(
                        remainingDisposition = RemainingDisposition.OMITTED,
                        terminalRemainingSets = 2
                    )
                }
            )
        }

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.PENDING, reloaded.status)
        assertNull(reloaded.resolvedAtMillis)
        assertNull(reloaded.entries[0].remainingDisposition)
        assertNull(reloaded.entries[0].terminalRemainingSets)
        assertEquals(beforeState, repository.scheduleState())
    }

    @Test
    fun resolvingAFinishedWorkoutEndsOnlyItsOwnOpenSession() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrences = repository.occurrences(activationId)
        val finished = occurrences[0]
        val other = occurrences[1]
        insertOpenSession("own", finished.id)
        insertOpenSession("other", other.id)
        insertOpenSession("unlinked", null)

        repository.resolveOccurrence(
            occurrenceId = finished.id,
            expectedRevision = finished.revision,
            status = OccurrenceStatus.FINISHED,
            resolvedAtMillis = 500L,
            entries = finished.entries
        )

        assertEquals(500L, session("own").endedAtMillis)
        assertNull(session("other").endedAtMillis)
        assertNull(session("unlinked").endedAtMillis)
    }

    @Test
    fun resolvingAPartiallyFinishedWorkoutEndsItsOpenSession() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrences = repository.occurrences(activationId)
        val partial = occurrences[0]
        insertOpenSession("partial", partial.id)

        repository.resolveOccurrence(
            occurrenceId = partial.id,
            expectedRevision = partial.revision,
            status = OccurrenceStatus.FINISHED_PARTIAL,
            resolvedAtMillis = 700L,
            entries = partial.entries
        )

        assertEquals(700L, session("partial").endedAtMillis)
    }

    @Test
    fun resolvingAWorkoutClosesTheUnlinkedSessionThatRecordedItsSets() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrences = repository.occurrences(activationId)
        val finished = occurrences[0]
        val other = occurrences[1]
        // Production logging opens the session without an occurrence link; attribution lives on the
        // recorded sets. The second session recorded nothing, and the third only another occurrence's
        // work, so neither belongs to this conclusion.
        insertOpenSession("live", null)
        insertOpenSession("idle", null)
        insertOpenSession("foreign", null)
        insertSetForOccurrence(1L, "live", finished.id, finished.entries[0].id)
        insertSetForOccurrence(2L, "foreign", other.id, other.entries[0].id)

        repository.resolveOccurrence(
            occurrenceId = finished.id,
            expectedRevision = finished.revision,
            status = OccurrenceStatus.FINISHED,
            resolvedAtMillis = 800L,
            entries = finished.entries
        )

        assertEquals(800L, session("live").endedAtMillis)
        assertNull(session("idle").endedAtMillis)
        assertNull(session("foreign").endedAtMillis)
    }

    @Test
    fun resolvingAnUntouchedSkipLeavesItsOpenSessionRunning() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val skipped = repository.occurrences(activationId)[0]
        insertOpenSession("skipped", skipped.id)

        // No recorded set means no attributable work; a skip this time genuinely touches nothing.
        repository.resolveOccurrence(
            occurrenceId = skipped.id,
            expectedRevision = skipped.revision,
            status = OccurrenceStatus.SKIPPED,
            resolvedAtMillis = 900L,
            entries = skipped.entries
        )

        assertEquals(OccurrenceStatus.SKIPPED, repository.getOccurrence(skipped.id)?.status)
        assertNull(session("skipped").endedAtMillis)
    }

    @Test
    fun aStaleResolutionLeavesTheSessionUntouched() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(activationId)[0]
        insertOpenSession("stale", occurrence.id)

        assertFailsWith<ScheduleException> {
            repository.resolveOccurrence(
                occurrenceId = occurrence.id,
                expectedRevision = occurrence.revision + 1,
                status = OccurrenceStatus.FINISHED,
                resolvedAtMillis = 500L,
                entries = occurrence.entries
            )
        }

        assertNull(session("stale").endedAtMillis)
        assertEquals(OccurrenceStatus.PENDING, repository.getOccurrence(occurrence.id)?.status)
    }

    private fun insertOpenSession(id: String, occurrenceId: Long?) {
        database.workoutSessionQueries.insertSessionWithOccurrence(
            id = id,
            startedAtMillis = 100L,
            endedAtMillis = null,
            localEpochDay = 20_000L,
            occurrenceId = occurrenceId
        )
    }

    private fun session(id: String) =
        database.workoutSessionQueries.selectSessionById(id).executeAsOne()

    /** Records one set the way production logging does: session and occurrence attribution only. */
    private fun insertSetForOccurrence(
        id: Long,
        sessionId: String,
        occurrenceId: Long,
        occurrenceEntryId: Long
    ) {
        database.workoutLogQueries.insertSetWithId(
            id = id,
            exerciseId = "back-squat",
            reps = 5L,
            weightKg = 100.0,
            performedAt = 200L,
            isWarmup = 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            occurrenceId = occurrenceId,
            occurrenceEntryId = occurrenceEntryId,
            loadKind = "EXTERNAL",
            durationSeconds = null
        )
    }

    @Test
    fun acceptAndActivateRollsBackTheCancelledPreviousBlockWhenTheInsertFails() = runTest {
        val first = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val firstOccurrenceIds = repository.occurrences(first).map { it.id }
        val beforeState = repository.scheduleState()
        // The previous block is cancelled before the replacement activation is inserted; fail the
        // insert so the cancellation must roll back with it.
        injectFault("BEFORE INSERT ON trainingActivation")

        assertFailsWith<Exception> {
            repository.acceptAndActivate(
                acceptedPlan = null,
                activation = activation(),
                scheduledEpochDays = listOf(20_010L, 20_012L),
                replaceActive = true
            )
        }

        assertEquals(ActivationStatus.ACTIVE, repository.getActivation(first)?.status)
        assertEquals(
            1,
            database.trainingScheduleQueries.selectAllActivations().executeAsList().size
        )
        assertEquals(
            2,
            database.trainingScheduleQueries.selectAllOccurrences().executeAsList().size
        )
        assertEquals(firstOccurrenceIds, repository.occurrences(first).map { it.id })
        assertEquals(beforeState, repository.scheduleState())
    }

    @Test
    fun updatingAnOccurrencePersistsStatusAndCompletionFields() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()

        repository.updateOccurrence(
            occurrence.copy(
                status = OccurrenceStatus.IN_PROGRESS,
                startedAtMillis = 111L,
                resolvedAtMillis = 222L,
                revision = 2
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.IN_PROGRESS, reloaded.status)
        assertEquals(111L, reloaded.startedAtMillis)
        assertEquals(222L, reloaded.resolvedAtMillis)
        assertEquals(2, reloaded.revision)
    }

    @Test
    fun replacingOccurrenceEntriesPreservesKeptIdsAndDeletesRemovedRows() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val original = occurrence.entries.single()

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                OccurrenceEntry(
                    position = 0,
                    exerciseId = "overhead-press",
                    exerciseName = "Overhead Press",
                    movementPattern = MovementPattern.VERTICAL_PUSH,
                    sets = 3,
                    reps = 8
                )
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(1, reloaded.entries.size)
        assertTrue(reloaded.entries[0].id != original.id)
        assertEquals("overhead-press", reloaded.entries[0].exerciseId)
    }

    @Test
    fun replacingOccurrenceEntriesInsertsNewRows() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val original = occurrence.entries.single()

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                original.copy(sets = 5),
                OccurrenceEntry(
                    position = 1,
                    exerciseId = "overhead-press",
                    exerciseName = "Overhead Press",
                    movementPattern = MovementPattern.VERTICAL_PUSH,
                    sets = 3,
                    reps = 8
                )
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(2, reloaded.entries.size)
        assertEquals(original.id, reloaded.entries[0].id)
        assertEquals(5, reloaded.entries[0].sets)
        assertEquals("overhead-press", reloaded.entries[1].exerciseId)
        assertTrue(reloaded.entries[1].id != 0L)
    }

    @Test
    fun ambiguousDuplicateOccurrenceEditLeavesSqlDelightRowsAndMetadataUnchanged() = runTest {
        SeedExerciseCatalog(database).seed()
        val id = repository.acceptAndActivate(null, activation(), listOf(null), false)
        val occurrence = repository.occurrences(id).first()
        val targets = listOf(
            SlotSetTargets.of(List(3) { SetTarget(reps = 8, weightKg = 30.0) }),
            SlotSetTargets.of(List(3) { SetTarget(reps = 6, weightKg = 35.0) })
        )
        val policy = SlotProgressionPolicy(minReps = 6, maxReps = 10, incrementKg = 2.5)
        val original = occurrence.entries.single()
        val duplicates = listOf(
            original.copy(position = 0, setTargets = targets[0], progressionPolicy = policy),
            original.copy(position = 1, setTargets = targets[1])
        )
        repository.replaceOccurrenceEntries(occurrence.id, duplicates)
        val before = requireNotNull(repository.getOccurrence(occurrence.id))
        val edit = EditUnstartedOccurrenceUseCase(
            repository,
            SqlDelightWorkoutLogRepository(database),
            SqlDelightExerciseCatalog(database)
        )

        assertFailsWith<ScheduleException> {
            edit(
                occurrence.id,
                listOf(OccurrenceEntryDraft("bench-press", sets = 3, reps = 8, weightKg = 30.0))
            )
        }

        assertEquals(before, repository.getOccurrence(occurrence.id))
        assertEquals(before.entries, repository.getOccurrence(occurrence.id)?.entries)
    }

    @Test
    fun deletingOccurrencesCascadesTheirEntries() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        repository.deleteOccurrencesForActivation(id)

        assertTrue(repository.occurrences(id).isEmpty())
        assertEquals(
            0,
            database.trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList().size
        )
    }

    @Test
    fun templateReferenceGuardReflectsStoredActivations() = runTest {
        assertFalse(repository.isTemplateReferenced(7L))
        repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        assertTrue(repository.isTemplateReferenced(7L))
        assertFalse(repository.isTemplateReferenced(8L))
    }

    @Test
    fun deletingAPlanDetachesTheActivationProvenance() = runTest {
        database.planHistoryQueries.insertPlan(
            engineId = "DETERMINISTIC",
            acceptedAt = 1L,
            weekNumber = 1L,
            cycleNumber = 1L
        )
        val planId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation().copy(sourcePlanId = planId),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        assertEquals(planId, requireNotNull(repository.getActivation(activationId)).sourcePlanId)

        SqlDelightPlanHistoryRepository(database).delete(planId)

        assertNull(requireNotNull(repository.getActivation(activationId)).sourcePlanId)
    }

    /** Installs a trigger that aborts [triggerClause] so a mid-transaction write can fail on demand. */
    private fun injectFault(triggerClause: String) {
        driver.execute(
            identifier = null,
            sql = "CREATE TRIGGER inject_fault $triggerClause " +
                "BEGIN SELECT RAISE(ABORT, 'injected'); END",
            parameters = 0
        ).value
    }

    @Test
    fun carriesAFrozenEntrysReasoningOntoItsOccurrenceAndReadsItBack() = runTest {
        val recorded = entryExplanation()
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(explanation = recorded),
            scheduledEpochDays = listOf(null, null),
            replaceActive = false
        )

        val stored = requireNotNull(repository.getActivation(activationId))
        assertEquals(recorded, stored.workouts.first().entries.first().explanation)

        // The occurrence copy inherits the frozen slot's reasoning at creation.
        val occurrenceId = requireNotNull(repository.scheduleState().selectedOccurrenceId)
        val occurrence = requireNotNull(repository.getOccurrence(occurrenceId))
        assertEquals(recorded, occurrence.entries.first().explanation)
    }

    @Test
    fun editingAnOccurrenceEntryClearsItsStoredReasoning() = runTest {
        repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(explanation = entryExplanation()),
            scheduledEpochDays = listOf(null, null),
            replaceActive = false
        )
        val occurrenceId = requireNotNull(repository.scheduleState().selectedOccurrenceId)
        val occurrence = requireNotNull(repository.getOccurrence(occurrenceId))
        assertNotNull(occurrence.entries.first().explanation)

        repository.replaceOccurrenceEntries(
            occurrenceId = occurrenceId,
            entries = occurrence.entries.map { it.copy(explanation = null) }
        )

        val updated = requireNotNull(repository.getOccurrence(occurrenceId))
        assertNull(updated.entries.first().explanation)
    }

    @Test
    fun anUnsupportedStoredEntryReasonVersionReadsAsUnavailable() = runTest {
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(explanation = entryExplanation()),
            scheduledEpochDays = listOf(null, null),
            replaceActive = false
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE entryTargetExplanation SET explanationVersion = 2 " +
                "WHERE entryKind = 'ACTIVATION'"
        )

        val stored = requireNotNull(repository.getActivation(activationId))

        assertNull(stored.workouts.first().entries.first().explanation)
    }

    @Test
    fun observesEachFrozenEntrysOwnReasoningWithoutMixingEntryKinds() = runTest {
        val first = entryExplanation()
        val second = entryExplanation().copy(
            baseline = LoadBaselineExplanation(
                source = LoadBaselineSource.MANUAL_PERSONAL_RECORD,
                manualFloorKg = 80.0,
                baselineKg = 80.0
            )
        )
        val expected = mapOf("bench-press" to first, "cable-row" to second)
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(explanation = first, secondExplanation = second),
            scheduledEpochDays = listOf(null, null),
            replaceActive = false
        )

        // The observed active block carries each entry's own reasoning.
        val active = requireNotNull(repository.observeActiveActivation().first())
        assertEquals(expected, active.workouts.associate { it.reasonedExercise() })

        // One batched read serves every occurrence, and each entry keeps its own reason.
        val observed = repository.observeOccurrences(activationId).first()
        assertEquals(2, observed.size)
        assertEquals(
            expected,
            observed.associate { it.entries.single().exerciseId to it.entries.single().explanation }
        )

        // Activation and occurrence rows share an entry id, so each read must stay kind-scoped:
        // with only the activation rows left, an occurrence entry must not borrow their reasoning.
        HistoricalDatabaseFixtures.exec(
            driver,
            "DELETE FROM entryTargetExplanation WHERE entryKind = 'OCCURRENCE'"
        )
        val reread = repository.observeOccurrences(activationId).first()
        assertEquals(
            listOf(null, null),
            reread.map { it.entries.single().explanation }
        )
    }

    private fun ActivationWorkout.reasonedExercise() =
        entries.single().exerciseId to entries.single().explanation

    private fun entryExplanation() = ExerciseTargetExplanation(
        baseline = LoadBaselineExplanation(
            source = LoadBaselineSource.RECENT_PERFORMANCE,
            recentEstimateKg = 100.0,
            qualifyingSetCount = 3,
            baselineKg = 100.0
        ),
        setCount = SetCountExplanation(
            baseSets = 3,
            deloadApplied = false,
            deloadVolumeScale = 0.7,
            fatigueEffect = FatigueSetEffect.NONE,
            finalSets = 3
        )
    )

    private fun activation(
        explanation: ExerciseTargetExplanation? = null,
        secondExplanation: ExerciseTargetExplanation? = null
    ) = TrainingActivation(
        templateId = 7L,
        templateRevision = 3,
        sourcePlanId = null,
        name = "Upper block",
        createdAtMillis = 1_000L,
        startEpochDay = 20_000L,
        mode = ScheduleMode.WEEKDAY,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        status = ActivationStatus.ACTIVE,
        workouts = listOf(
            ActivationWorkout(
                position = 0,
                name = "Day 1",
                focus = SplitFocus.PUSH,
                entries = listOf(
                    ActivationEntry(
                        position = 0,
                        exerciseId = "bench-press",
                        exerciseName = "Barbell Bench Press",
                        movementPattern = MovementPattern.HORIZONTAL_PUSH,
                        requiredEquipment = setOf(EquipmentTag("barbell")),
                        involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.6),
                        isUnilateral = false,
                        sets = 3,
                        reps = 8,
                        weightKg = 82.5,
                        explanation = explanation
                    )
                )
            ),
            ActivationWorkout(
                position = 1,
                name = "Day 2",
                focus = SplitFocus.PULL,
                entries = listOf(
                    ActivationEntry(
                        position = 0,
                        exerciseId = "cable-row",
                        exerciseName = "Cable Row",
                        movementPattern = MovementPattern.HORIZONTAL_PULL,
                        requiredEquipment = setOf(EquipmentTag("cable")),
                        involvements = mapOf(MuscleGroup.LATS to 0.7),
                        isUnilateral = false,
                        sets = 3,
                        reps = 10,
                        weightKg = 60.0,
                        explanation = secondExplanation
                    )
                )
            )
        )
    )
}
