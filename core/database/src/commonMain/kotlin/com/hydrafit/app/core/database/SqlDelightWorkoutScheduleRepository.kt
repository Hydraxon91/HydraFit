package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.schedule.ActivationEntry as DomainActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout as DomainActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry as DomainOccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleException
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

class SqlDelightWorkoutScheduleRepository(private val database: HydraFitDatabase) :
    WorkoutScheduleRepository {
    private val queries = database.trainingScheduleQueries

    override fun observeScheduleState(): Flow<DomainWorkoutScheduleState> =
        queries.selectScheduleState().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() ?: DomainWorkoutScheduleState() }

    override fun observeActiveActivation(): Flow<DomainTrainingActivation?> {
        val activation = queries.selectActiveActivation().asFlow().mapToOneOrNull(
            Dispatchers.Default
        )
        val workouts = queries.selectAllActivationWorkouts().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllActivationEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(activation, workouts, entries) { row, workoutRows, entryRows ->
            row?.toDomain(workoutRows, entryRows)
        }
    }

    override fun observeOccurrences(activationId: Long): Flow<List<DomainWorkoutOccurrence>> {
        val occurrences =
            queries.selectOccurrencesForActivation(
                activationId
            ).asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllOccurrenceEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(occurrences, entries) { occurrenceRows, entryRows ->
            occurrenceRows.map { it.toDomain(entryRows) }
        }
    }

    override suspend fun scheduleState(): DomainWorkoutScheduleState =
        queries.selectScheduleState().executeAsOneOrNull()?.toDomain()
            ?: DomainWorkoutScheduleState()

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

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: DomainTrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long = database.transactionWithResult {
        val active = queries.selectActiveActivation().executeAsOneOrNull()
        if (active != null) {
            if (!replaceActive) {
                throw ScheduleException("A training block is already active")
            }
            queries.updateActivationHeader(
                name = active.name,
                mode = active.mode,
                weekdayMask = active.weekdayMask,
                status = ActivationStatus.CANCELLED.name,
                endedAtMillis = activation.createdAtMillis,
                revision = active.revision + 1,
                id = active.id
            )
        }

        val acceptedPlanId = acceptedPlan?.let { insertAcceptedPlan(it) }

        queries.insertActivation(
            templateId = activation.templateId,
            templateRevision = activation.templateRevision?.toLong(),
            sourcePlanId = acceptedPlanId ?: activation.sourcePlanId,
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

        val occurrenceIds = mutableListOf<Long>()
        activation.workouts.forEachIndexed { index, workout ->
            queries.insertActivationWorkout(
                activationId = activationId,
                sourceWorkoutId = null,
                position = index.toLong(),
                name = workout.name,
                focus = workout.focus?.name
            )
            val workoutId = queries.lastInsertedActivationWorkoutId().executeAsOne()
            // The real ids the just-inserted activation entries received; each occurrence entry
            // copies its source entry so a logged set stays attributable to the prescribed slot.
            val entryIds = workout.entries.map { entry ->
                queries.insertActivationEntry(
                    workoutId = workoutId,
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    movementPattern = entry.movementPattern.name,
                    requiredEquipment = encodeEquipment(entry.requiredEquipment),
                    involvements = entry.involvements?.let { encodeInvolvements(it) },
                    isUnilateral = if (entry.isUnilateral) 1L else 0L,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability.name,
                    loadKind = entry.loadKind.name
                )
                queries.lastInsertedActivationEntryId().executeAsOne()
            }

            queries.insertOccurrence(
                activationId = activationId,
                activationWorkoutId = workoutId,
                queuePosition = index.toLong(),
                scheduledEpochDay = scheduledEpochDays.getOrNull(index),
                notBeforeEpochDay = null,
                startedAtMillis = null,
                resolvedAtMillis = null,
                status = OccurrenceStatus.PENDING.name,
                revision = 1L
            )
            val occurrenceId = queries.lastInsertedOccurrenceId().executeAsOne()
            occurrenceIds += occurrenceId

            workout.entries.forEachIndexed { entryIndex, entry ->
                queries.insertOccurrenceEntry(
                    occurrenceId = occurrenceId,
                    sourceActivationEntryId = entryIds[entryIndex],
                    position = entry.position.toLong(),
                    exerciseId = entry.exerciseId,
                    exerciseName = entry.exerciseName,
                    movementPattern = entry.movementPattern.name,
                    requiredEquipment = encodeEquipment(entry.requiredEquipment),
                    involvements = entry.involvements?.let { encodeInvolvements(it) },
                    isUnilateral = if (entry.isUnilateral) 1L else 0L,
                    sets = entry.sets.toLong(),
                    reps = entry.reps.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability.name,
                    loadKind = entry.loadKind.name,
                    remainingDisposition = null,
                    terminalRemainingSets = null
                )
            }
        }

        queries.upsertScheduleState(
            activeActivationId = activationId,
            selectedOccurrenceId = occurrenceIds.firstOrNull(),
            legacyFallbackEnabled = 0L
        )
        activationId
    }

    override suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<DomainOccurrenceEntry>
    ): DomainWorkoutScheduleState = database.transactionWithResult {
        val occurrence = queries.selectOccurrenceById(occurrenceId).executeAsOneOrNull()
            ?: throw ScheduleException("That workout no longer exists")
        val state = queries.selectScheduleState().executeAsOneOrNull()
        if (state?.activeActivationId != occurrence.activationId) {
            throw ScheduleException("That workout is not in the active block")
        }
        if (occurrence.revision != expectedRevision.toLong()) {
            throw ScheduleException("That workout changed; refresh and try again")
        }

        applyEntryChanges(occurrenceId, entries)

        queries.updateOccurrence(
            queuePosition = occurrence.queuePosition,
            scheduledEpochDay = occurrence.scheduledEpochDay,
            notBeforeEpochDay = occurrence.notBeforeEpochDay,
            startedAtMillis = occurrence.startedAtMillis,
            resolvedAtMillis = resolvedAtMillis,
            status = status.name,
            revision = occurrence.revision + 1,
            id = occurrenceId
        )

        val next = queries.selectOccurrencesForActivation(occurrence.activationId).executeAsList()
            .filter { it.id != occurrenceId && it.status !in RESOLVED_STATUS_NAMES }
            .minByOrNull { it.queuePosition }
        val legacyFallback = state.legacyFallbackEnabled != 0L
        queries.upsertScheduleState(
            activeActivationId = occurrence.activationId,
            selectedOccurrenceId = next?.id,
            legacyFallbackEnabled = if (legacyFallback) 1L else 0L
        )
        DomainWorkoutScheduleState(
            activeActivationId = occurrence.activationId,
            selectedOccurrenceId = next?.id,
            legacyFallbackEnabled = legacyFallback
        )
    }

    private fun insertAcceptedPlan(plan: AcceptedPlan): Long {
        val planQueries = database.planHistoryQueries
        planQueries.insertPlan(
            engineId = plan.engine.name,
            acceptedAt = plan.acceptedAtMillis,
            weekNumber = plan.weekNumber.toLong(),
            cycleNumber = plan.cycleNumber.toLong()
        )
        val planId = planQueries.lastInsertedPlanId().executeAsOne()
        plan.days.forEach { day ->
            planQueries.insertDay(
                planId = planId,
                dayIndex = day.dayIndex.toLong(),
                focus = day.focus.name
            )
            val dayId = planQueries.lastInsertedPlanId().executeAsOne()
            day.exercises.forEachIndexed { position, exercise ->
                planQueries.insertEntry(
                    dayId = dayId,
                    position = position.toLong(),
                    exerciseId = exercise.exerciseId,
                    sets = exercise.sets.toLong(),
                    reps = exercise.reps.toLong(),
                    exerciseName = exercise.name,
                    movementPattern = exercise.movementPattern.name,
                    suggestedWeightKg = exercise.suggestedWeightKg,
                    loadCapability = exercise.loadCapability.name,
                    loadKind = exercise.loadKind.name
                )
            }
        }
        return planId
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
            applyEntryChanges(occurrenceId, entries)
        }
    }

    private fun applyEntryChanges(occurrenceId: Long, entries: List<DomainOccurrenceEntry>) {
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

    override suspend fun deleteOccurrencesForActivation(activationId: Long) {
        queries.transaction {
            queries.selectOccurrencesForActivation(
                activationId
            ).executeAsList().forEach { occurrence ->
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
            loadCapability = loadCapability.name,
            loadKind = loadKind.name,
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong()
        )
        return copy(
            id = queries.lastInsertedOccurrenceEntryId().executeAsOne(),
            position = position
        )
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
            loadCapability = loadCapability.name,
            loadKind = loadKind.name,
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong(),
            id = id
        )
    }

    private fun TrainingActivation.toDomainWithChildren(
        queries: TrainingScheduleQueries
    ): DomainTrainingActivation {
        val workouts = queries.selectActivationWorkoutsForActivation(id).executeAsList()
        val entries = workouts.flatMap {
            queries.selectActivationEntriesForWorkout(it.id).executeAsList()
        }
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
        weightKg = weightKg,
        loadCapability = decodeLoadCapability(loadCapability),
        loadKind = decodeLoadKind(loadKind)
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
        sourceActivationEntryId = sourceActivationEntryId, position = position.toInt(),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        movementPattern = decodeMovementPattern(movementPattern),
        requiredEquipment = decodeEquipment(requiredEquipment),
        involvements = involvements?.let { decodeInvolvements(it) },
        isUnilateral = isUnilateral != 0L,
        sets = sets.toInt(),
        reps = reps.toInt(),
        weightKg = weightKg,
        loadCapability = decodeLoadCapability(loadCapability),
        loadKind = decodeLoadKind(loadKind),
        remainingDisposition = remainingDisposition?.toRemainingDisposition(),
        terminalRemainingSets = terminalRemainingSets?.toInt()
    )

    private fun WorkoutScheduleState.toDomain(): DomainWorkoutScheduleState =
        DomainWorkoutScheduleState(
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

    private companion object {
        val RESOLVED_STATUS_NAMES: Set<String> = setOf(
            OccurrenceStatus.FINISHED.name,
            OccurrenceStatus.FINISHED_PARTIAL.name,
            OccurrenceStatus.SKIPPED.name
        )
    }
}
