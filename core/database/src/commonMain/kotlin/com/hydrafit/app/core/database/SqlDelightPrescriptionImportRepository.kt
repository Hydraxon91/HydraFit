package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.PrescriptionImportCommitFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionImportException
import com.hydrafit.app.core.domain.transfer.PrescriptionImportPreview
import com.hydrafit.app.core.domain.transfer.PrescriptionImportRepository
import com.hydrafit.app.core.domain.transfer.PrescriptionImportResult

/**
 * Appends an approved prescription import in one transaction.
 *
 * Routines are inserted as new templates with fresh local identities, so an imported routine can
 * never overwrite a local one and never becomes an accepted plan, an activation or a schedule change.
 * Custom definitions are inserted only when the id is unused; an id that appeared since the preview
 * refuses the whole commit instead of overwriting a row a local edit may own. Nothing outside the
 * template tables and those new exercise rows is written.
 */
class SqlDelightPrescriptionImportRepository(
    private val database: HydraFitDatabase,
    private val timeProvider: TimeProvider
) : PrescriptionImportRepository {

    private val templateQueries = database.routineTemplateQueries
    private val exerciseQueries = database.exerciseQueries

    override suspend fun importPrescriptions(
        preview: PrescriptionImportPreview
    ): PrescriptionImportResult {
        if (preview.routines.isEmpty()) {
            throw PrescriptionImportException(
                PrescriptionImportCommitFailure.NOTHING_TO_IMPORT,
                "There is no routine to import"
            )
        }
        if (preview.problems.isNotEmpty()) {
            // canImport is the app's gate, but a caller that ignores it must not import a subset.
            throw PrescriptionImportException(
                PrescriptionImportCommitFailure.PREVIEW_NOT_IMPORTABLE,
                "The preview still carries unresolved problems"
            )
        }
        var importedRoutines = 0
        var createdExercises = 0
        database.transaction {
            preview.customExercisesToCreate.forEach { exercise ->
                if (exerciseQueries.selectById(exercise.id).executeAsOneOrNull() != null) {
                    throw PrescriptionImportException(
                        PrescriptionImportCommitFailure.IDENTITY_ALREADY_EXISTS,
                        "${exercise.name} already exists locally and will not be overwritten"
                    )
                }
                val requiredEquipment = encodeEquipment(exercise.requiredEquipment)
                val involvements = encodeInvolvements(exercise.effectiveInvolvements)
                exerciseQueries.insertCustom(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = requiredEquipment,
                    movementPattern = exercise.movementPattern.name,
                    isUnilateral = if (exercise.isUnilateral) 1L else 0L,
                    loadCapability = exercise.loadCapability.name,
                    involvements = involvements
                )
                database.refreshExerciseSearchName(exercise.id)
                createdExercises++
            }
            val now = timeProvider.nowMillis()
            preview.routines.forEach { routine -> insertRoutine(routine, now) }
            importedRoutines = preview.routines.size
        }
        return PrescriptionImportResult(importedRoutines, createdExercises)
    }

    /** Inserts one routine as new rows, preserving workout and slot order. */
    private fun insertRoutine(routine: RoutineTemplate, nowMillis: Long) {
        templateQueries.insertTemplate(
            name = routine.name,
            revision = 0L,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            archivedAtMillis = null,
            sourcePlanId = null
        )
        val templateId = templateQueries.lastInsertedTemplateId().executeAsOne()
        routine.workouts.sortedBy { it.position }.forEachIndexed { index, workout ->
            templateQueries.insertWorkout(
                templateId = templateId,
                position = index.toLong(),
                name = workout.name,
                focus = workout.focus?.name
            )
            val workoutId = templateQueries.lastInsertedWorkoutId().executeAsOne()
            workout.entries.sortedBy { it.position }.forEachIndexed { entryIndex, entry ->
                templateQueries.insertEntry(
                    workoutId = workoutId,
                    position = entryIndex.toLong(),
                    exerciseId = entry.exerciseId,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadKind = entry.loadKind.name
                )
            }
        }
    }
}
