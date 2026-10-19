package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.AiLoadExplanation
import com.hydrafit.app.core.domain.engine.ArmCoverageUnmetReason
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.FrozenPlanExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.LoadConversionExplanation
import com.hydrafit.app.core.domain.engine.LoadProgressionExplanation
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.ProgressionOutcome
import com.hydrafit.app.core.domain.engine.SetCountExplanation
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightPlanHistoryRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightPlanHistoryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightPlanHistoryRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun isNullBeforeAnythingIsAccepted() = runTest {
        assertNull(repository.latest())
        assertNull(repository.observeLatest().first())
    }

    @Test
    fun persistsAnAcceptedPlanAndReadsItBack() = runTest {
        repository.accept(
            plan(
                engine = PlannerEngineId.GEMINI_API,
                acceptedAt = 100L,
                weekNumber = 3,
                cycleNumber = 2
            )
        )

        val latest = requireNotNull(repository.latest())

        assertEquals(PlannerEngineId.GEMINI_API, latest.engine)
        assertEquals(100L, latest.acceptedAtMillis)
        assertEquals(3, latest.weekNumber)
        assertEquals(2, latest.cycleNumber)
        assertTrue(latest.id > 0)
        assertEquals(2, latest.days.size)
        val day = latest.days.first()
        assertEquals(SplitFocus.PUSH, day.focus)
        assertEquals(listOf("bench-press", "overhead-press"), day.exercises.map { it.exerciseId })
        assertEquals("Barbell Bench Press", day.exercises.first().name)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, day.exercises.first().movementPattern)
        assertEquals(82.5, day.exercises.first().suggestedWeightKg)
        assertEquals(SplitFocus.LEGS, latest.days.last().focus)
        assertEquals(emptyList(), latest.days.last().exercises)
    }

    @Test
    fun latestIsTheMostRecentlyAcceptedPlan() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        repository.accept(plan(engine = PlannerEngineId.LOCAL_LLM, acceptedAt = 2L))

        assertEquals(PlannerEngineId.LOCAL_LLM, requireNotNull(repository.latest()).engine)
    }

    @Test
    fun observeLatestEmitsTheAcceptedPlan() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 5L))

        val observed = requireNotNull(repository.observeLatest().first())

        assertEquals(PlannerEngineId.DETERMINISTIC, observed.engine)
        assertEquals(5L, observed.acceptedAtMillis)
    }

    @Test
    fun observeHistoryEmitsPlansNewestFirst() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        repository.accept(plan(engine = PlannerEngineId.LOCAL_LLM, acceptedAt = 2L))

        val history = repository.observeHistory().first()

        assertEquals(
            listOf(PlannerEngineId.LOCAL_LLM, PlannerEngineId.DETERMINISTIC),
            history.map { it.engine }
        )
    }

    @Test
    fun clearRemovesTheHistory() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))

        repository.clear()

        assertNull(repository.latest())
    }

    @Test
    fun deleteRemovesThePlanItsDaysAndEntries() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        repository.accept(plan(engine = PlannerEngineId.LOCAL_LLM, acceptedAt = 2L))
        val toDelete = requireNotNull(repository.latest())

        repository.delete(toDelete.id)

        assertEquals(PlannerEngineId.DETERMINISTIC, requireNotNull(repository.latest()).engine)
        assertEquals(1, database.planHistoryQueries.selectAllPlans().executeAsList().size)
        assertEquals(2, database.planHistoryQueries.selectAllDays().executeAsList().size)
        assertEquals(2, database.planHistoryQueries.selectAllEntries().executeAsList().size)
    }

    @Test
    fun substituteUpdatesExerciseIdAndNameAtPosition() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        val accepted = requireNotNull(repository.latest())

        repository.substitute(
            planId = accepted.id,
            dayIndex = 0,
            position = 1,
            newExerciseId = "dumbbell-press",
            newExerciseName = "Dumbbell Press",
            newWeightKg = 30.0,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )

        val reloaded = requireNotNull(repository.latest())
        assertEquals(accepted.id, reloaded.id)
        assertEquals(accepted.acceptedAtMillis, reloaded.acceptedAtMillis)
        val swapped = reloaded.days.first().exercises[1]
        assertEquals("dumbbell-press", swapped.exerciseId)
        assertEquals("Dumbbell Press", swapped.name)
        assertEquals(30.0, swapped.suggestedWeightKg)
    }

    @Test
    fun substituteLeavesOtherEntriesUntouched() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        val accepted = requireNotNull(repository.latest())

        repository.substitute(
            planId = accepted.id,
            dayIndex = 0,
            position = 1,
            newExerciseId = "dumbbell-press",
            newExerciseName = "Dumbbell Press",
            newWeightKg = null,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )

        val reloaded = requireNotNull(repository.latest())
        val untouched = reloaded.days.first().exercises[0]
        assertEquals("bench-press", untouched.exerciseId)
        assertEquals("Barbell Bench Press", untouched.name)
        assertEquals(82.5, untouched.suggestedWeightKg)
    }

    @Test
    fun persistsTheVolumeExplanationAndClearsItOnSubstitution() = runTest {
        repository.accept(
            plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L).copy(
                armCoverage = coverage(),
                volumeAttribution = PlanAttribution.DETERMINISTIC
            )
        )

        val loaded = requireNotNull(repository.latest())
        assertEquals(PlanAttribution.DETERMINISTIC, loaded.volumeAttribution)
        assertEquals(coverage(), loaded.armCoverage)
        assertEquals(VolumeExplanationStatus.AVAILABLE, loaded.volumeExplanationStatus)
        assertEquals(1, loaded.volumeAssessmentVersion)
        // Adding the explanation does not alter the retained prescription.
        assertEquals(
            listOf("bench-press", "overhead-press"),
            loaded.days.first().exercises.map {
                it.exerciseId
            }
        )

        repository.substitute(
            planId = loaded.id,
            dayIndex = 0,
            position = 1,
            newExerciseId = "dumbbell-press",
            newExerciseName = "Dumbbell Press",
            newWeightKg = null,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )

        val afterSwap = requireNotNull(repository.latest())
        assertEquals(emptyList(), afterSwap.armCoverage)
        assertNull(afterSwap.volumeAttribution)
        // The invalidated status survives reload, so absence is not mistaken for a legacy plan.
        assertEquals(
            VolumeExplanationStatus.INVALIDATED_BY_SUBSTITUTION,
            afterSwap.volumeExplanationStatus
        )
    }

    @Test
    fun aFailureWhileWritingTheExplanationRollsBackTheWholeAcceptance() = runTest {
        driver.execute(
            identifier = null,
            sql = "CREATE TRIGGER fail_state BEFORE INSERT ON planVolumeExplanationState " +
                "BEGIN SELECT RAISE(ABORT, 'boom'); END",
            parameters = 0
        )

        assertFailsWith<Exception> {
            repository.accept(
                plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L).copy(
                    armCoverage = coverage(),
                    volumeAttribution = PlanAttribution.DETERMINISTIC
                )
            )
        }

        assertNull(repository.latest())
        assertEquals(0, database.planHistoryQueries.selectAllPlans().executeAsList().size)
        assertEquals(0, database.planHistoryQueries.selectAllDays().executeAsList().size)
    }

    @Test
    fun aNoOpSubstitutionLeavesTheVolumeExplanationUntouched() = runTest {
        repository.accept(
            plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L).copy(
                armCoverage = coverage(),
                volumeAttribution = PlanAttribution.DETERMINISTIC
            )
        )
        val accepted = requireNotNull(repository.latest())

        // A missing day, then a missing position: neither changes a slot, so the frozen assessment
        // and the plan must survive intact.
        repository.substitute(
            planId = accepted.id,
            dayIndex = 5,
            position = 0,
            newExerciseId = "dumbbell-press",
            newExerciseName = "Dumbbell Press",
            newWeightKg = null,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )
        repository.substitute(
            planId = accepted.id,
            dayIndex = 0,
            position = 9,
            newExerciseId = "dumbbell-press",
            newExerciseName = "Dumbbell Press",
            newWeightKg = null,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )

        val reloaded = requireNotNull(repository.latest())
        assertEquals(coverage(), reloaded.armCoverage)
        assertEquals(PlanAttribution.DETERMINISTIC, reloaded.volumeAttribution)
        assertEquals(VolumeExplanationStatus.AVAILABLE, reloaded.volumeExplanationStatus)
        assertEquals(
            listOf("bench-press", "overhead-press"),
            reloaded.days.first().exercises.map { it.exerciseId }
        )
    }

    @Test
    fun aLegacyPlanHasNoVolumeExplanation() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))

        val loaded = requireNotNull(repository.latest())

        assertEquals(emptyList(), loaded.armCoverage)
        assertNull(loaded.volumeAttribution)
    }

    @Test
    fun persistsSlotReasoningWithItsPlanContext() = runTest {
        repository.accept(explainedPlan())

        val stored = repository.latest()!!

        assertEquals(PlanAttribution.DETERMINISTIC, stored.explanation?.attribution)
        assertEquals(false, stored.explanation?.isDeload)
        assertEquals(0.7, stored.explanation?.deloadVolumeScale)
        assertEquals(0.8, stored.explanation?.deloadIntensityScale)
        val slot = requireNotNull(stored.days.first().exercises.first().explanation)
        assertEquals(LoadBaselineSource.RECENT_PERFORMANCE, slot.baseline?.source)
        assertEquals(101.0, slot.baseline?.baselineKg)
        assertEquals(3, slot.baseline?.qualifyingSetCount)
        assertEquals(ProgressionOutcome.APPLIED, slot.progression?.outcome)
        assertEquals(1, slot.progression?.increments)
        assertEquals(2.5, slot.progression?.deltaKg)
        assertEquals(8, slot.conversion?.reps)
        assertEquals(0.765, slot.conversion?.intensityFraction)
        assertEquals(FatigueSetEffect.NONE, slot.setCount?.fatigueEffect)
        assertEquals(3, slot.setCount?.finalSets)
    }

    @Test
    fun persistsAModelProposalAndTheAppAdjustmentsAppliedToIt() = runTest {
        repository.accept(aiExplainedPlan())

        val stored = repository.latest()!!

        assertNotNull(stored.explanation)
        val slot = requireNotNull(stored.days.first().exercises.first().explanation)
        // A model proposal is described by its own record, never by a rep-intensity conversion.
        assertNull(slot.conversion)
        val ai = requireNotNull(slot.aiAdjustment)
        assertEquals(150.0, ai.proposedKg)
        assertTrue(ai.proposedAccepted)
        assertTrue(ai.deloadScaled)
        assertEquals(0.8, ai.deloadIntensityScale)
        assertEquals(77.5, ai.progressionBoundKg)
        assertTrue(ai.boundedByProgression)
        assertEquals(60.0, ai.equipmentCeilingKg)
        assertTrue(ai.cappedByEquipment)
    }

    @Test
    fun substitutingASlotClearsOnlyThatSlotsReasoning() = runTest {
        repository.accept(explainedPlan())

        repository.substitute(
            planId = 1L,
            dayIndex = 0,
            position = 0,
            newExerciseId = "barbell-row",
            newExerciseName = "Barbell Row",
            newWeightKg = 60.0,
            newLoadCapability = ExerciseLoadCapability.EXTERNAL,
            newLoadKind = LoadKind.EXTERNAL
        )

        val stored = repository.latest()!!

        assertNull(stored.days.first().exercises[0].explanation)
        assertNotNull(stored.days.first().exercises[1].explanation)
        // The plan-level context is not a slot reason, so a substitution leaves it valid.
        assertNotNull(stored.explanation)
    }

    @Test
    fun aPlanWithoutRecordedReasoningReadsAsUnrecorded() = runTest {
        repository.accept(plan(PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))

        val stored = repository.latest()!!

        assertNull(stored.explanation)
        assertTrue(stored.days.flatMap { it.exercises }.all { it.explanation == null })
    }

    @Test
    fun anIncompleteStoredReasonReadsAsUnavailableWithoutAffectingTheRestOfThePlan() = runTest {
        // A plan, one day and two slots, written directly so the fixture can hold a partial record.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistory(id, engineId, acceptedAt, weekNumber, cycleNumber) " +
                "VALUES (5, 'DETERMINISTIC', 1, 1, 1)"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistoryDay(id, planId, dayIndex, focus) VALUES (9, 5, 0, 'PUSH')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistoryEntry(id, dayId, position, exerciseId, sets, reps, " +
                "exerciseName, movementPattern, suggestedWeightKg, loadCapability, loadKind) " +
                "VALUES (11, 9, 0, 'bench-press', 3, 8, 'Bench Press', 'HORIZONTAL_PUSH', " +
                "80.0, 'EXTERNAL', 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planHistoryEntry(id, dayId, position, exerciseId, sets, reps, " +
                "exerciseName, movementPattern, suggestedWeightKg, loadCapability, loadKind) " +
                "VALUES (12, 9, 1, 'overhead-press', 3, 8, 'Overhead Press', 'VERTICAL_PUSH', " +
                "null, 'EXTERNAL', 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanationState VALUES (5, 'DETERMINISTIC', 0, 0.7, 0.8, 1)"
        )
        // Present but incomplete: the progression outcome has no matching numbers, so the whole
        // record must be reported as unavailable.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanation(planId, dayIndex, position, explanationVersion, " +
                "progressionOutcome) VALUES (5, 0, 0, 1, 'APPLIED')"
        )
        // A complete neighbouring record must still read back.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO planTargetExplanation(planId, dayIndex, position, explanationVersion, " +
                "setBaseSets, setDeloadApplied, setDeloadVolumeScale, setFatigueEffect, " +
                "setFinalSets, unavailableReason) " +
                "VALUES (5, 0, 1, 1, 3, 0, 0.7, 'NONE', 3, 'NOT_LOAD_TRACKED')"
        )

        val stored = requireNotNull(repository.latest())

        val exercises = stored.days.single().exercises
        // The partial record is unavailable, not reconstructed with defaults.
        assertNull(exercises[0].explanation)
        assertNotNull(exercises[1].explanation)
        assertEquals(
            LoadUnavailableReason.NOT_LOAD_TRACKED,
            exercises[1].explanation?.unavailableReason
        )
        // The plan, its slots and the plan-level context are unaffected.
        assertNotNull(stored.explanation)
        assertEquals(listOf("bench-press", "overhead-press"), exercises.map { it.exerciseId })
    }

    @Test
    fun anUnsupportedStoredSlotReasonVersionReadsAsUnavailable() = runTest {
        repository.accept(explainedPlan())
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE planTargetExplanation SET explanationVersion = 2 WHERE planId = 1"
        )

        val stored = requireNotNull(repository.latest())

        assertNotNull(stored.explanation)
        assertNull(stored.days.first().exercises.first().explanation)
    }

    @Test
    fun anUnsupportedStoredPlanContextVersionHidesItsSlotReasons() = runTest {
        repository.accept(explainedPlan())
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE planTargetExplanationState SET explanationVersion = 2 WHERE planId = 1"
        )

        val stored = requireNotNull(repository.latest())

        assertNull(stored.explanation)
        assertTrue(stored.days.flatMap { it.exercises }.all { it.explanation == null })
    }

    private fun aiExplainedPlan(): AcceptedPlan {
        val base = plan(PlannerEngineId.GEMINI_API, acceptedAt = 1L)
        val recorded = ExerciseTargetExplanation(
            aiAdjustment = AiLoadExplanation(
                proposedKg = 150.0,
                proposedAccepted = true,
                deloadScaled = true,
                deloadIntensityScale = 0.8,
                progressionBoundKg = 77.5,
                boundedByProgression = true,
                equipmentCeilingKg = 60.0,
                cappedByEquipment = true
            ),
            setCount = SetCountExplanation(
                baseSets = 3,
                deloadApplied = true,
                deloadVolumeScale = 0.7,
                fatigueEffect = FatigueSetEffect.NONE,
                finalSets = 2
            )
        )
        return base.copy(
            explanation = FrozenPlanExplanation(
                attribution = PlanAttribution.AI_GENERATED,
                isDeload = true,
                deloadVolumeScale = 0.7,
                deloadIntensityScale = 0.8
            ),
            days = base.days.map { day ->
                if (day.dayIndex != 0) {
                    day
                } else {
                    day.copy(exercises = day.exercises.map { it.copy(explanation = recorded) })
                }
            }
        )
    }

    private fun explainedPlan(): AcceptedPlan {
        val base = plan(PlannerEngineId.DETERMINISTIC, acceptedAt = 1L)
        val recorded = slotExplanation()
        return base.copy(
            explanation = FrozenPlanExplanation(
                attribution = PlanAttribution.DETERMINISTIC,
                isDeload = false,
                deloadVolumeScale = 0.7,
                deloadIntensityScale = 0.8
            ),
            days = base.days.map { day ->
                if (day.dayIndex != 0) {
                    day
                } else {
                    day.copy(exercises = day.exercises.map { it.copy(explanation = recorded) })
                }
            }
        )
    }

    private fun slotExplanation() = ExerciseTargetExplanation(
        baseline = LoadBaselineExplanation(
            source = LoadBaselineSource.RECENT_PERFORMANCE,
            recentEstimateKg = 101.0,
            qualifyingSetCount = 3,
            baselineKg = 101.0
        ),
        progression = LoadProgressionExplanation(
            outcome = ProgressionOutcome.APPLIED,
            increments = 1,
            deltaKg = 2.5,
            roundToKg = 2.5,
            successStreakSize = 3,
            failureStreakSize = 3
        ),
        conversion = LoadConversionExplanation(
            reps = 8,
            intensityFraction = 0.765,
            deloadIntensityScale = 1.0,
            roundToKg = 2.5,
            uncappedWorkingKg = 80.0,
            equipmentCeilingKg = null,
            equipmentCeilingBinds = false
        ),
        setCount = SetCountExplanation(
            baseSets = 3,
            deloadApplied = false,
            deloadVolumeScale = 0.7,
            fatigueEffect = FatigueSetEffect.NONE,
            finalSets = 3
        )
    )

    private fun coverage() = listOf(
        ArmMuscleCoverage(
            muscle = MuscleGroup.BICEPS,
            targetSets = 4,
            isTargetEnforced = true,
            directIsolationSets = 4,
            estimatedOtherInvolvementCredits = 1.5,
            unmetReason = null
        ),
        ArmMuscleCoverage(
            muscle = MuscleGroup.TRICEPS,
            targetSets = 4,
            isTargetEnforced = true,
            directIsolationSets = 2,
            estimatedOtherInvolvementCredits = 0.75,
            unmetReason = ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE
        )
    )

    @Test
    fun roundTripsATimedSlotMeasure() = runTest {
        repository.accept(
            plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L).copy(
                days = listOf(
                    AcceptedDay(
                        dayIndex = 0,
                        focus = SplitFocus.PUSH,
                        exercises = listOf(
                            AcceptedExercise(
                                exerciseId = "plank",
                                sets = 3,
                                reps = null,
                                durationSeconds = 45,
                                name = "Plank",
                                movementPattern = MovementPattern.CORE
                            )
                        )
                    )
                )
            )
        )

        val slot = requireNotNull(repository.latest()).days.single().exercises.single()

        assertNull(slot.reps)
        assertEquals(45, slot.durationSeconds)
    }

    @Test
    fun setMeasureUpdatesOneSlotInPlace() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        val planId = requireNotNull(repository.latest()).id

        repository.setMeasure(
            planId = planId,
            dayIndex = 0,
            position = 0,
            reps = null,
            durationSeconds = 45
        )

        val timed = requireNotNull(repository.latest()).days.first().exercises.first()
        assertNull(timed.reps)
        assertEquals(45, timed.durationSeconds)
        // The plan keeps its id and acceptance time: an UPDATE, not a re-accept.
        assertEquals(planId, requireNotNull(repository.latest()).id)
        assertEquals(1L, requireNotNull(repository.latest()).acceptedAtMillis)

        repository.setMeasure(
            planId = planId,
            dayIndex = 0,
            position = 0,
            reps = 12,
            durationSeconds = null
        )

        val counted = requireNotNull(repository.latest()).days.first().exercises.first()
        assertEquals(12, counted.reps)
        assertNull(counted.durationSeconds)
    }

    @Test
    fun setMeasureOnAMissingSlotIsANoOp() = runTest {
        repository.accept(plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L))
        val planId = requireNotNull(repository.latest()).id

        repository.setMeasure(planId, dayIndex = 5, position = 0, reps = 12, durationSeconds = null)
        repository.setMeasure(planId, dayIndex = 0, position = 9, reps = null, durationSeconds = 45)

        val slot = requireNotNull(repository.latest()).days.first().exercises.first()
        assertEquals(8, slot.reps)
        assertNull(slot.durationSeconds)
    }

    @Test
    fun substituteClearsOrderedTargetsWhenTheLoadKindChanges() = runTest {
        val targets = com.hydrafit.app.core.domain.engine.SlotSetTargets.of(
            listOf(
                com.hydrafit.app.core.domain.engine.SetTarget(
                    reps = 8,
                    loadKind = LoadKind.EXTERNAL,
                    weightKg = 60.0
                )
            )
        )
        val original = plan(engine = PlannerEngineId.DETERMINISTIC, acceptedAt = 1L).let { plan ->
            plan.copy(
                days = plan.days.map { day ->
                    day.copy(
                        exercises = day.exercises.map {
                            if (it.exerciseId == "overhead-press") {
                                it.copy(setTargets = targets)
                            } else {
                                it
                            }
                        }
                    )
                }
            )
        }
        repository.accept(original)
        val accepted = requireNotNull(repository.latest())

        repository.substitute(
            planId = accepted.id,
            dayIndex = 0,
            position = 1,
            newExerciseId = "push-up",
            newExerciseName = "Push-up",
            newWeightKg = null,
            newLoadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
            newLoadKind = LoadKind.BODYWEIGHT
        )

        // External ordered targets cannot describe a bodyweight slot; the stored column is cleared.
        assertNull(repository.latest()!!.days.first().exercises[1].setTargets)
    }

    private fun plan(
        engine: PlannerEngineId,
        acceptedAt: Long,
        weekNumber: Int = 1,
        cycleNumber: Int = 1
    ) = AcceptedPlan(
        engine = engine,
        acceptedAtMillis = acceptedAt,
        weekNumber = weekNumber,
        cycleNumber = cycleNumber,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.PUSH,
                exercises = listOf(
                    exercise(
                        id = "bench-press",
                        name = "Barbell Bench Press",
                        pattern = MovementPattern.HORIZONTAL_PUSH,
                        suggestedWeightKg = 82.5
                    ),
                    exercise(
                        id = "overhead-press",
                        name = "Overhead Press",
                        pattern = MovementPattern.VERTICAL_PUSH,
                        suggestedWeightKg = null
                    )
                )
            ),
            AcceptedDay(
                dayIndex = 1,
                focus = SplitFocus.LEGS,
                exercises = emptyList()
            )
        )
    )

    private fun exercise(
        id: String,
        name: String,
        pattern: MovementPattern,
        suggestedWeightKg: Double?
    ) = AcceptedExercise(
        exerciseId = id,
        sets = 3,
        reps = 8,
        name = name,
        movementPattern = pattern,
        suggestedWeightKg = suggestedWeightKg
    )
}
