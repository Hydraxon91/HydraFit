package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.routine.RoutineEntry as DomainRoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate as DomainRoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.RoutineWorkout as DomainRoutineWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightRoutineTemplateRepository(private val database: HydraFitDatabase) :
    RoutineTemplateRepository {
    private val queries = database.routineTemplateQueries

    override fun observeAll(): Flow<List<DomainRoutineTemplate>> {
        val templates = queries.selectAllTemplates().asFlow().mapToList(Dispatchers.Default)
        val workouts = queries.selectAllWorkouts().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(templates, workouts, entries) { templateRows, workoutRows, entryRows ->
            templateRows.map { row -> row.toDomain(workoutRows, entryRows) }
        }
    }

    override suspend fun get(id: Long): DomainRoutineTemplate? {
        val template = queries.selectTemplate(id).executeAsOneOrNull() ?: return null
        val workouts = queries.selectWorkoutsForTemplate(id).executeAsList()
        val entries = workouts.flatMap { queries.selectEntriesForWorkout(it.id).executeAsList() }
        return template.toDomain(workouts, entries)
    }

    override suspend fun save(template: DomainRoutineTemplate): Long =
        queries.transactionWithResult {
            val templateId = if (template.id == 0L) {
                queries.insertTemplate(
                    name = template.name,
                    revision = template.revision.toLong(),
                    createdAtMillis = template.createdAtMillis,
                    updatedAtMillis = template.updatedAtMillis,
                    archivedAtMillis = template.archivedAtMillis,
                    sourcePlanId = template.sourcePlanId
                )
                queries.lastInsertedTemplateId().executeAsOne()
            } else {
                queries.updateTemplate(
                    name = template.name,
                    revision = template.revision.toLong(),
                    updatedAtMillis = template.updatedAtMillis,
                    archivedAtMillis = template.archivedAtMillis,
                    sourcePlanId = template.sourcePlanId,
                    id = template.id
                )
                template.id
            }

            val keptWorkoutIds = mutableSetOf<Long>()
            template.workouts.forEachIndexed { index, workout ->
                val workoutId = if (workout.id == 0L) {
                    queries.insertWorkout(
                        templateId = templateId,
                        position = index.toLong(),
                        name = workout.name,
                        focus = workout.focus?.name
                    )
                    queries.lastInsertedWorkoutId().executeAsOne()
                } else {
                    queries.updateWorkout(
                        position = index.toLong(),
                        name = workout.name,
                        focus = workout.focus?.name,
                        id = workout.id
                    )
                    workout.id
                }
                keptWorkoutIds += workoutId

                val keptEntryIds = mutableSetOf<Long>()
                workout.entries.forEachIndexed { entryIndex, entry ->
                    val entryId = if (entry.id == 0L) {
                        queries.insertEntry(
                            workoutId = workoutId,
                            position = entryIndex.toLong(),
                            exerciseId = entry.exerciseId,
                            sets = entry.sets.toLong(),
                            reps = entry.reps.toLong(),
                            weightKg = entry.weightKg,
                            loadKind = entry.loadKind.name
                        )
                        queries.lastInsertedEntryId().executeAsOne()
                    } else {
                        queries.updateEntry(
                            position = entryIndex.toLong(),
                            exerciseId = entry.exerciseId,
                            sets = entry.sets.toLong(),
                            reps = entry.reps.toLong(),
                            weightKg = entry.weightKg,
                            loadKind = entry.loadKind.name,
                            id = entry.id
                        )
                        entry.id
                    }
                    keptEntryIds += entryId
                }
                queries.selectEntryIdsForWorkout(workoutId).executeAsList()
                    .filterNot { it in keptEntryIds }
                    .forEach { queries.deleteEntry(it) }
            }

            queries.selectWorkoutIdsForTemplate(templateId).executeAsList()
                .filterNot { it in keptWorkoutIds }
                .forEach { workoutId ->
                    queries.deleteEntriesForWorkout(workoutId)
                    queries.deleteWorkout(workoutId)
                }
            templateId
        }

    override suspend fun setArchived(id: Long, archivedAtMillis: Long?) {
        queries.setArchived(archivedAtMillis = archivedAtMillis, id = id)
    }

    override suspend fun isReferencedByActivation(id: Long): Boolean =
        database.trainingScheduleQueries.countActivationsForTemplate(id).executeAsOne() > 0L

    override suspend fun delete(id: Long) {
        queries.transaction {
            queries.selectWorkoutIdsForTemplate(id).executeAsList().forEach {
                queries.deleteEntriesForWorkout(it)
            }
            queries.deleteWorkoutsForTemplate(id)
            queries.deleteTemplate(id)
        }
    }

    private fun RoutineTemplate.toDomain(
        allWorkouts: List<RoutineWorkout>,
        allEntries: List<RoutineEntry>
    ): DomainRoutineTemplate {
        val entriesByWorkout = allEntries.groupBy { it.workoutId }
        val workouts = allWorkouts
            .filter { it.templateId == id }
            .sortedBy { it.position }
            .map { row ->
                DomainRoutineWorkout(
                    id = row.id,
                    position = row.position.toInt(),
                    name = row.name,
                    focus = row.focus?.toFocusOrNull(),
                    entries = entriesByWorkout[row.id].orEmpty()
                        .sortedBy { it.position }
                        .map { entry ->
                            DomainRoutineEntry(
                                id = entry.id,
                                position = entry.position.toInt(),
                                exerciseId = entry.exerciseId,
                                sets = entry.sets.toInt(),
                                reps = entry.reps.toInt(),
                                weightKg = entry.weightKg,
                                loadKind = decodeLoadKind(entry.loadKind)
                            )
                        }
                )
            }
        return DomainRoutineTemplate(
            id = id,
            name = name,
            revision = revision.toInt(),
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            archivedAtMillis = archivedAtMillis,
            sourcePlanId = sourcePlanId,
            workouts = workouts
        )
    }

    private fun String.toFocusOrNull(): SplitFocus? =
        SplitFocus.entries.firstOrNull { it.name == this }
}
