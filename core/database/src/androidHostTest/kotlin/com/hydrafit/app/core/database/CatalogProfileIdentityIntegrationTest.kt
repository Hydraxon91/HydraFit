package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatch
import com.hydrafit.app.core.domain.equipment.CatalogProfileMatcher
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class CatalogProfileIdentityIntegrationTest {
    @Test
    fun profileReadsAndStartupDedupeLeaveAcceptedCustomIdentityAndReferencesUntouched() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            SeedExerciseCatalog(database).seed()
            SeedEquipmentCatalog(database).seed()
            val customRepository = SqlDelightCustomExerciseRepository(database)
            val custom = customRepository.add(
                name = "Pullup",
                requiredEquipment = emptySet(),
                involvements = mapOf(MuscleGroup.ABS to 1.0),
                movementPattern = MovementPattern.CORE
            )
            val history = SqlDelightPlanHistoryRepository(database)
            history.accept(
                AcceptedPlan(
                    engine = PlannerEngineId.DETERMINISTIC,
                    acceptedAtMillis = 1L,
                    days = listOf(
                        AcceptedDay(
                            dayIndex = 0,
                            focus = SplitFocus.FULL_BODY,
                            exercises = listOf(
                                AcceptedExercise(
                                    exerciseId = custom.id,
                                    sets = 3,
                                    reps = 5,
                                    name = custom.name,
                                    movementPattern = custom.movementPattern,
                                    suggestedWeightKg = 20.0
                                )
                            )
                        )
                    )
                )
            )
            val routines = SqlDelightRoutineTemplateRepository(database)
            val templateId = routines.save(
                RoutineTemplate(
                    name = "Custom routine",
                    workouts = listOf(
                        RoutineWorkout(
                            name = "Day 1",
                            entries = listOf(
                                RoutineEntry(exerciseId = custom.id, sets = 3, reps = 5)
                            )
                        )
                    )
                )
            )
            val schedule = SqlDelightWorkoutScheduleRepository(database)
            schedule.acceptAndActivate(
                acceptedPlan = null,
                activation = TrainingActivation(
                    templateId = templateId,
                    name = "Custom block",
                    createdAtMillis = 1L,
                    startEpochDay = 20_000L,
                    mode = ScheduleMode.SEQUENCE,
                    weekdays = emptySet(),
                    status = ActivationStatus.ACTIVE,
                    workouts = listOf(
                        ActivationWorkout(
                            name = "Day 1",
                            entries = listOf(
                                ActivationEntry(
                                    exerciseId = custom.id,
                                    exerciseName = custom.name,
                                    movementPattern = custom.movementPattern,
                                    sets = 3,
                                    reps = 5,
                                    weightKg = 20.0
                                )
                            )
                        )
                    )
                ),
                scheduledEpochDays = listOf(null),
                replaceActive = false
            )
            SqlDelightWorkoutLogRepository(database).add(
                WorkoutSet(
                    exerciseId = custom.id,
                    reps = 5,
                    weightKg = 20.0,
                    performedAtMillis = 1L
                )
            )
            database.personalRecordQueries.upsert(custom.id, 30.0, 5L, 1L, "EXTERNAL")
            database.exercisePreferenceQueries.upsert(custom.id, "PREFER")
            database.exerciseExclusionQueries.upsert(custom.id, null)

            fun storedRows() = listOf(
                database.exerciseQueries.selectAll().executeAsList(),
                database.exerciseOverrideQueries.selectAll().executeAsList(),
                database.workoutLogQueries.selectAllSets().executeAsList(),
                database.planHistoryQueries.selectAllEntries().executeAsList(),
                database.routineTemplateQueries.selectAllEntries().executeAsList(),
                database.trainingScheduleQueries.selectAllActivationEntries().executeAsList(),
                database.trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList(),
                database.personalRecordQueries.selectAll().executeAsList(),
                database.exercisePreferenceQueries.selectAll().executeAsList(),
                database.exerciseExclusionQueries.selectAll().executeAsList()
            )

            val beforeRead = storedRows()
            val catalog = SqlDelightExerciseCatalog(database)
            val match = assertIs<CatalogProfileMatch.Unique>(
                CatalogProfileMatcher.match("Pullup", catalog.profileCandidates())
            )
            assertEquals("pull-up", match.candidate.catalogId)
            assertEquals(beforeRead, storedRows())

            // A non-colliding translation remains this custom id on update too; no remapping.
            customRepository.update(
                id = custom.id,
                name = "Langhantel-Bankdrücken",
                requiredEquipment = custom.requiredEquipment,
                involvements = custom.involvements,
                movementPattern = custom.movementPattern
            )
            val beforeStartup = storedRows()
            SeedExerciseCatalog(database).seed()
            CustomExerciseDedupe(database).run()
            assertEquals(beforeStartup, storedRows())
        } finally {
            driver.close()
        }
    }
}
