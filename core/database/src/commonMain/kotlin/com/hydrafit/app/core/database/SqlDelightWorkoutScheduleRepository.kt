package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.schedule.ActivationEntry as DomainActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout as DomainActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry as DomainOccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation as DomainTrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence as DomainWorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState as DomainWorkoutScheduleState
import com.hydrafit.app.core.domain.schedule.toWeekdayMask
import com.hydrafit.app.core.domain.schedule.weekdaysOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SqlDelightWorkoutScheduleRepository(database: HydraFitDatabase) : WorkoutScheduleRepository {
    private val queries = database.trainingScheduleQueries

    override fun observeScheduleState(): Flow<DomainWorkoutScheduleState> =
        queries.selectScheduleState().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() ?: DomainWorkoutScheduleState() }

    override fun observeActiveActivation(): Flow<DomainTrainingActivation?> {
        val activation = queries.selectActiveActivation().asFlow().mapToOneOrNull(Dispatchers.Default)
        val workouts = queries.selectAllActivationWorkouts().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllActivationEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(activation, workouts, entries) { row, workoutRows, entryRows ->
            row?.toDomain(workoutRows, entryRows)
        }
    }

    override fun observeOccurrences(activationId: Long): Flow<List<DomainWorkoutOccurrence>> {
        val occurrences =
            queries.selectOccurrencesForActivation(activationId).asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllOccurrenceEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(occurrences, entries) { occurrenceRows, entryRows ->
            occurrenceRows.map { it.toDomain(entryRows) }
        }
    }

    override suspend fun scheduleState(): DomainWorkoutScheduleState =
        queries.selectScheduleState().executeAsOneOrNull()?.toDomain() ?: DomainWorkoutScheduleState()

    override suspend fun setScheduleState(state: DomainWorkoutScheduleState) {
        queries.upsertScheduleState(
            activeActivationId = state.activeActivationId,
            selectedOccurrenceId = state.selectedOccurrenceId,
            legacyFallbackEnabled = if (state.legacyFallbackEnabled) 1L else 0L
        )
    }

    override suspend fun activeActivation(): DomainTrainingActivation? =
        queries.selectActiveActivation().executeAsOneOrNull()?.toDomainWithChildren(queries)

    override suspend fun getActivation(id: Long): DomainTrainingActivation? =
        queries.selectActivationById(id).executeAsOneOrNull()?.toDomainWithChildren(queries)

    override suspend fun occurrences(activationId: Long): List<DomainWorkoutOccurrence> {
        val occurrenceRows = queries.selectOccurrencesForActivation(activationId).executeAsList()
        val entries = occurrenceRows.flatMap {
            queries.selectOccurrenceEntriesForOccurrence(it.id).executeAsList()
        }
        return occurrenceRows.map { it.toDomain(entries) }
    }

    override suspend fun getOccurrence(id: Long): DomainWorkoutOccurrence? {
        val occurrence = queries.selectOccurrenceById(id).executeAsOneOrNull() ?: return null
        return occurrence.toDomain(queries.selectOccurrenceEntriesForOccurrence(id).executeAsList())
    }

    override suspend fun insertActivation(activation: DomainTrainingActivation): Long =
        queries.transactionWithResult {
            queries.insertActivation(
                templateId = activation.templateId,
                templateRevision = activation.templateRevision?.toLong(),
                sourcePlanId = activation.sourcePlanId,
                name = activation.name,
                createdAtMillis = activation.createdAtMillis,
                startEpochDay = activation.startEpochDay,
                mode = activation.mode.name,
                weekdayMask = activation.weekdays.toWeekdayMask().toLong(),
                status = activation.status.name,
                weekNumber = activation.weekNumber?.toLong(),
                cycleNumber = activation.cycleNumber?.toLong(),
                endedAtMillis = activation.endedAtMillis,
                revision = activation.revision.toLong()
            )
            val activationId = queries.lastInsertedActivationId().executeAsOne()
            activation.workouts.forEachIndexed { index, workout ->
                queries.insertActivationWorkout(
                    activationId = activationId,
                    sourceWorkoutId = null,
                    position = index.toLong(),
                    name = workout.name,
                    focus = workout.focus?.name
                )
                val workoutId = queries.lastInsertedActivationWorkoutId().executeAsOne()
                workout.entries.forEachIndexed { entryIndex, entry ->
                    queries.insertActivationEntry(
                        workoutId = workoutId,
                        position = entryIndex.toLong(),
                        exerciseId = entry.exerciseId,
                        exerciseName = entry.exerciseName,
                        movementPattern = entry.movementPattern.name,
                        requiredEquipment = encodeEquipment(entry.requiredEquipment),
                        involvements = entry.involvements?.let { encodeInvolvements(it) },
                        isUnilateral = if (entry.isUnilateral) 1L else 0L,
                        sets = entry.sets.toLong(),
                        reps = entry.reps.toLong(),
                        weightKg = entry.weightKg
                    )
                }
            }
            activationId
        }

    override suspend fun insertOccurrences(
        occurrences: List<DomainWorkoutOccurrence>
    ): List<DomainWorkoutOccurrence> = queries.transactionWithResult {
        occurrences.map { occurrence ->
            queries.insertOccurrence(
                activationId = occurrence.activationId,
                activationWorkoutId = occurrence.activationWorkoutId,
                queuePosition = occurrence.queuePosition.toLong(),
                scheduledEpochDay = occurrence.scheduledEpochDay,
                notBeforeEpochDay = occurrence.notBeforeEpochDay,
                startedAtMillis = occurrence.startedAtMillis,
                resolvedAtMillis = occurrence.resolvedAtMillis,
                status = occurrence.status.name,
                revision = occurrence.revision.toLong()
            )
            val occurrenceId = queries.lastInsertedOccurrenceId().executeAsOne()
            val savedEntries = occurrence.entries.map { entry ->
                entry.insert(queries, occurrenceId)
            }
            occurrence.copy(id = occurrenceId, entries = savedEntries)
        }
    }

    override suspend fun updateActivationHeader(activation: DomainTrainingActivation) {
        queries.updateActivationHeader(
            name = activation.name,
            mode = activation.mode.name,
            weekdayMask = activation.weekdays.toWeekdayMask().toLong(),
            status = activation.status.name,
            endedAtMillis = activation.endedAtMillis,
            revision = activation.revision.toLong(),
            id = activation.id
        )
    }

    override suspend fun updateOccurrence(occurrence: DomainWorkoutOccurrence) {
        queries.updateOccurrence(
            queuePosition = occurrence.queuePosition.toLong(),
            scheduledEpochDay = occurrence.scheduledEpochDay,
            notBeforeEpochDay = occurrence.notBeforeEpochDay,
            startedAtMillis = occurrence.startedAtMillis,
            resolvedAtMillis = occurrence.resolvedAtMillis,
            status = occurrence.status.name,
            revision = occurrence.revision.toLong(),
            id = occurrence.id
        )
    }

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<DomainOccurrenceEntry>
    ) {
        queries.transaction {
            val keptIds = mutableSetOf<Long>()
            entries.forEachIndexed { index, entry ->
                if (entry.id == 0L) {
                    keptIds += entry.insert(queries, occurrenceId, position = index).id
                } else {
                    entry.update(queries, position = index)
                    keptIds += entry.id
                }
            }
            queries.selectOccurrenceEntryIdsForOccurrence(occurrenceId).executeAsList()
                .filterNot { it in keptIds }
                .forEach { queries.deleteOccurrenceEntry(it) }
        }
    }

    override suspend fun deleteOccurrencesForActivation(activationId: Long) {
        queries.transaction {
            queries.selectOccurrencesForActivation(activationId).executeAsList().forEach { occurrence ->
                queries.deleteOccurrenceEntriesForOccurrence(occurrence.id)
            }
            queries.deleteOccurrencesForActivation(activationId)
        }
    }

    override suspend fun isTemplateReferenced(templateId: Long): Boolean =
        queries.countActivationsForTemplate(templateId).executeAsOne() > 0L

    private fun DomainOccurrenceEntry.insert(
        queries: TrainingScheduleQueries,
        occurrenceId: Long,
        position: Int = this.position
    ): DomainOccurrenceEntry {
        queries.insertOccurrenceEntry(
            occurrenceId = occurrenceId,
            sourceActivationEntryId = sourceActivationEntryId,
            position = position.toLong(),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            movementPattern = movementPattern.name,
            requiredEquipment = encodeEquipment(requiredEquipment),
            involvements = involvements?.let { encodeInvolvements(it) },
            isUnilateral = if (isUnilateral) 1L else 0L,
            sets = sets.toLong(),
            reps = reps.toLong(),
            weightKg = weightKg,
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong()
        )
        return copy(id = queries.lastInsertedOccurrenceEntryId().executeAsOne(), position = position)
    }

    private fun DomainOccurrenceEntry.update(queries: TrainingScheduleQueries, position: Int) {
        queries.updateOccurrenceEntry(
            position = position.toLong(),
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            movementPattern = movementPattern.name,
            requiredEquipment = encodeEquipment(requiredEquipment),
            involvements = involvements?.let { encodeInvolvements(it) },
            isUnilateral = if (isUnilateral) 1L else 0L,
            sets = sets.toLong(),
            reps = reps.toLong(),
            weightKg = weightKg,
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong(),
            id = id
        )
    }

    private fun TrainingActivation.toDomainWithChildren(
        queries: TrainingScheduleQueries
    ): DomainTrainingActivation {
        val workouts = queries.selectActivationWorkoutsForActivation(id).executeAsList()
        val entries = workouts.flatMap { queries.selectActivationEntriesForWorkout(it.id).executeAsList() }
        return toDomain(workouts, entries)
    }

    private fun TrainingActivation.toDomain(
        allWorkouts: List<ActivationWorkout>,
        allEntries: List<ActivationEntry>
    ): DomainTrainingActivation {
        val entriesByWorkout = allEntries.groupBy { it.workoutId }
        val workouts = allWorkouts
            .filter { it.activationId == id }
            .sortedBy { it.position }
            .map { row ->
                DomainActivationWorkout(
                    id = row.id,
                    position = row.position.toInt(),
                    name = row.name,
                    focus = row.focus?.toFocusOrNull(),
                    entries = entriesByWorkout[row.id].orEmpty()
                        .sortedBy { it.position }
                        .map { it.toDomain() }
                )
            }
        return DomainTrainingActivation(
            id = id,
            templateId = templateId,
            templateRevision = templateRevision?.toInt(),
            sourcePlanId = sourcePlanId,
            name = name,
            createdAtMillis = createdAtMillis,
            startEpochDay = startEpochDay,
            mode = mode.toScheduleMode(),
            weekdays = weekdaysOf(weekdayMask.toInt()),
            status = status.toActivationStatus(),
            weekNumber = weekNumber?.toInt(),
            cycleNumber = cycleNumber?.toInt(),
            endedAtMillis = endedAtMillis,
            revision = revision.toInt(),
            workouts = workouts
        )
    }

    private fun ActivationEntry.toDomain(): DomainActivationEntry = DomainActivationEntry(
        id = id,
        position = position.toInt(),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        movementPattern = decodeMovementPattern(movementPattern),
        requiredEquipment = decodeEquipment(requiredEquipment),
        involvements = involvements?.let { decodeInvolvements(it) },
        isUnilateral = isUnilateral != 0L,
        sets = sets.toInt(),
        reps = reps.toInt(),
        weightKg = weightKg
    )

    private fun WorkoutOccurrence.toDomain(
        allEntries: List<OccurrenceEntry>
    ): DomainWorkoutOccurrence = DomainWorkoutOccurrence(
        id = id,
        activationId = activationId,
        activationWorkoutId = activationWorkoutId,
        queuePosition = queuePosition.toInt(),
        scheduledEpochDay = scheduledEpochDay,
        notBeforeEpochDay = notBeforeEpochDay,
        startedAtMillis = startedAtMillis,
        resolvedAtMillis = resolvedAtMillis,
        status = status.toOccurrenceStatus(),
        revision = revision.toInt(),
        entries = allEntries
            .filter { it.occurrenceId == id }
            .sortedBy { it.position }
            .map { it.toDomain() }
    )

    private fun OccurrenceEntry.toDomain(): DomainOccurrenceEntry = DomainOccurrenceEntry(
        id = id,
        sourceActivationEntryId = sourceActivationEntryId,
        position = position.toInt(),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        movementPattern = decodeMovementPattern(movementPattern),
        requiredEquipment = decodeEquipment(requiredEquipment),
        involvements = involvements?.let { decodeInvolvements(it) },
        isUnilateral = isUnilateral != 0L,
        sets = sets.toInt(),
        reps = reps.toInt(),
        weightKg = weightKg,
        remainingDisposition = remainingDisposition?.toRemainingDisposition(),
        terminalRemainingSets = terminalRemainingSets?.toInt()
    )

    private fun WorkoutScheduleState.toDomain(): DomainWorkoutScheduleState = DomainWorkoutScheduleState(
        activeActivationId = activeActivationId,
        selectedOccurrenceId = selectedOccurrenceId,
        legacyFallbackEnabled = legacyFallbackEnabled != 0L
    )

    private fun String.toFocusOrNull(): SplitFocus? =
        SplitFocus.entries.firstOrNull { it.name == this }

    private fun String.toActivationStatus(): ActivationStatus =
        ActivationStatus.entries.firstOrNull { it.name == this } ?: ActivationStatus.ACTIVE

    private fun String.toScheduleMode(): ScheduleMode =
        ScheduleMode.entries.firstOrNull { it.name == this } ?: ScheduleMode.SEQUENCE

    private fun String.toOccurrenceStatus(): OccurrenceStatus =
        OccurrenceStatus.entries.firstOrNull { it.name == this } ?: OccurrenceStatus.PENDING

    private fun String.toRemainingDisposition(): RemainingDisposition? =
        RemainingDisposition.entries.firstOrNull { it.name == this }
}
