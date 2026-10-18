package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.schedule.ActivationEntry as DomainActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout as DomainActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry as DomainOccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementException
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementFailure
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementRepository
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation as DomainTrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence as DomainWorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState as DomainWorkoutScheduleState
import com.hydrafit.app.core.domain.schedule.toWeekdayMask
import com.hydrafit.app.core.domain.schedule.weekdaysOf
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SqlDelightWorkoutScheduleRepository(
    private val database: HydraFitDatabase,
    private val gate: WorkoutReminderDeliveryGate = WorkoutReminderDeliveryGate()
) : WorkoutScheduleRepository,
    OccurrenceReplacementRepository {
    private val queries = database.trainingScheduleQueries
    private val planWriter = AcceptedPlanWriter(database)

    override fun observeScheduleState(): Flow<DomainWorkoutScheduleState> =
        queries.selectScheduleState().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() ?: DomainWorkoutScheduleState() }

    override fun observeActiveActivation(): Flow<DomainTrainingActivation?> {
        val activation = queries.selectActiveActivation().asFlow().mapToOneOrNull(
            Dispatchers.Default
        )
        val workouts = queries.selectAllActivationWorkouts().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllActivationEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = database.entryTargetExplanationQueries.selectAll()
            .asFlow().mapToList(Dispatchers.Default)
        return combine(activation, workouts, entries, explanations) {
                row,
                workoutRows,
                entryRows,
                explanationRows
            ->
            row?.toDomain(
                workoutRows,
                entryRows,
                entryExplanations(explanationRows, ACTIVATION_ENTRY_KIND)
            )
        }
    }

    override fun observeOccurrences(activationId: Long): Flow<List<DomainWorkoutOccurrence>> {
        val occurrences =
            queries.selectOccurrencesForActivation(
                activationId
            ).asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllOccurrenceEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = database.entryTargetExplanationQueries.selectAll()
            .asFlow().mapToList(Dispatchers.Default)
        return combine(occurrences, entries, explanations) {
                occurrenceRows,
                entryRows,
                explanationRows
            ->
            val byEntryId = entryExplanations(explanationRows, OCCURRENCE_ENTRY_KIND)
            occurrenceRows.map { it.toDomain(entryRows, byEntryId) }
        }
    }

    override suspend fun scheduleState(): DomainWorkoutScheduleState =
        queries.selectScheduleState().executeAsOneOrNull()?.toDomain()
            ?: DomainWorkoutScheduleState()

    override suspend fun setScheduleState(state: DomainWorkoutScheduleState) {
        gate.mutate {
            queries.upsertScheduleState(
                activeActivationId = state.activeActivationId,
                selectedOccurrenceId = state.selectedOccurrenceId,
                legacyFallbackEnabled = if (state.legacyFallbackEnabled) 1L else 0L
            )
        }
    }

    override suspend fun activeActivation(): DomainTrainingActivation? {
        val row = queries.selectActiveActivation().executeAsOneOrNull() ?: return null
        val explanations = entryExplanations(
            database.entryTargetExplanationQueries.selectAll().executeAsList(),
            ACTIVATION_ENTRY_KIND
        )
        return row.toDomainWithChildren(queries, explanations)
    }

    override suspend fun getActivation(id: Long): DomainTrainingActivation? {
        val row = queries.selectActivationById(id).executeAsOneOrNull() ?: return null
        val explanations = entryExplanations(
            database.entryTargetExplanationQueries.selectAll().executeAsList(),
            ACTIVATION_ENTRY_KIND
        )
        return row.toDomainWithChildren(queries, explanations)
    }

    override suspend fun occurrences(activationId: Long): List<DomainWorkoutOccurrence> {
        val occurrenceRows = queries.selectOccurrencesForActivation(activationId).executeAsList()
        val entries = occurrenceRows.flatMap {
            queries.selectOccurrenceEntriesForOccurrence(it.id).executeAsList()
        }
        val explanations = entryExplanations(
            database.entryTargetExplanationQueries.selectAll().executeAsList(),
            OCCURRENCE_ENTRY_KIND
        )
        return occurrenceRows.map { it.toDomain(entries, explanations) }
    }

    override suspend fun getOccurrence(id: Long): DomainWorkoutOccurrence? {
        val occurrence = queries.selectOccurrenceById(id).executeAsOneOrNull() ?: return null
        val explanations = entryExplanations(
            database.entryTargetExplanationQueries.selectAll().executeAsList(),
            OCCURRENCE_ENTRY_KIND
        )
        return occurrence.toDomain(
            queries.selectOccurrenceEntriesForOccurrence(id).executeAsList(),
            explanations
        )
    }

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: DomainTrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long = gate.mutate {
        acceptAndActivateLocked(acceptedPlan, activation, scheduledEpochDays, replaceActive)
    }

    private fun acceptAndActivateLocked(
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

        val acceptedPlanId = acceptedPlan?.let { planWriter.insert(it) }

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
                    reps = entry.reps?.toLong(),
                    durationSeconds = entry.durationSeconds?.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability.name,
                    loadKind = entry.loadKind.name,
                    progressionPolicy = entry.progressionPolicy?.let(
                        SlotProgressionPolicyJson::encode
                    )
                )
                val entryId = queries.lastInsertedActivationEntryId().executeAsOne()
                writeEntryExplanation(ACTIVATION_ENTRY_KIND, entryId, entry.explanation)
                entryId
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
                    reps = entry.reps?.toLong(),
                    durationSeconds = entry.durationSeconds?.toLong(),
                    weightKg = entry.weightKg,
                    loadCapability = entry.loadCapability.name,
                    loadKind = entry.loadKind.name,
                    progressionPolicy = entry.progressionPolicy
                        ?.let(SlotProgressionPolicyJson::encode),
                    remainingDisposition = null,
                    terminalRemainingSets = null
                )
                // The occurrence copy inherits the frozen slot's reasoning at creation; a later
                // replacement or edit clears it (see applyEntryChanges and the entry update path).
                writeEntryExplanation(
                    OCCURRENCE_ENTRY_KIND,
                    queries.lastInsertedOccurrenceEntryId().executeAsOne(),
                    entry.explanation
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
    ): DomainWorkoutScheduleState = gate.mutate {
        resolveOccurrenceLocked(occurrenceId, expectedRevision, status, resolvedAtMillis, entries)
    }

    private fun resolveOccurrenceLocked(
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

    override suspend fun updateActivationHeader(activation: DomainTrainingActivation) {
        gate.mutate {
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
    }

    override suspend fun updateOccurrence(occurrence: DomainWorkoutOccurrence) {
        gate.mutate {
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
    }

    override suspend fun replaceUntouchedEntry(
        occurrenceId: Long,
        expectedRevision: Int,
        replacement: DomainOccurrenceEntry,
        nowMillis: Long
    ) = gate.mutate {
        replaceUntouchedEntryLocked(occurrenceId, expectedRevision, replacement, nowMillis)
    }

    private fun replaceUntouchedEntryLocked(
        occurrenceId: Long,
        expectedRevision: Int,
        replacement: DomainOccurrenceEntry,
        nowMillis: Long
    ) {
        database.transaction {
            fun reject(reason: OccurrenceReplacementFailure): Nothing =
                throw OccurrenceReplacementException(reason)
            val occurrence = queries.selectOccurrenceById(occurrenceId).executeAsOneOrNull()
                ?: reject(OccurrenceReplacementFailure.STALE_CONTEXT)
            val state = queries.selectScheduleState().executeAsOneOrNull()
            val original = queries.selectOccurrenceEntryById(replacement.id).executeAsOneOrNull()
                ?: reject(OccurrenceReplacementFailure.STALE_CONTEXT)
            if (state?.activeActivationId != occurrence.activationId ||
                (
                    state.selectedOccurrenceId != null &&
                        state.selectedOccurrenceId != occurrenceId
                    ) ||
                occurrence.status in RESOLVED_STATUS_NAMES ||
                occurrence.revision != expectedRevision.toLong() ||
                original.occurrenceId != occurrenceId ||
                original.sets != replacement.sets.toLong() ||
                original.reps != replacement.reps?.toLong() ||
                original.durationSeconds != replacement.durationSeconds?.toLong() ||
                original.position != replacement.position.toLong() ||
                original.sourceActivationEntryId != replacement.sourceActivationEntryId ||
                original.remainingDisposition != null ||
                replacement.remainingDisposition != null ||
                replacement.terminalRemainingSets != null
            ) {
                reject(OccurrenceReplacementFailure.STALE_CONTEXT)
            }
            // Mirrors WorkoutSet.freezesOccurrenceSlot: a set attributed to this entry freezes it,
            // and the entry id is authoritative, so an entry-only row (no occurrence id) still
            // counts. An occurrence-level set freezes only this same exercise's slot whose entry
            // link was lost. Unplanned extra exercises are deliberately not attributed here, so they
            // do not freeze unrelated slots.
            val frozen = database.workoutLogQueries
                .selectSetsForOccurrenceEntry(replacement.id)
                .executeAsList()
                .isNotEmpty() ||
                database.workoutLogQueries.selectSetsForOccurrence(occurrenceId)
                    .executeAsList()
                    .any { row ->
                        row.occurrenceEntryId == null && row.exerciseId == original.exerciseId
                    }
            if (frozen) {
                reject(OccurrenceReplacementFailure.RECORDED_SLOT)
            }
            val candidate = SqlDelightExerciseCatalog(database).findById(replacement.exerciseId)
                ?: reject(OccurrenceReplacementFailure.UNAVAILABLE)
            // Availability and ceilings follow the active equipment profile, not the legacy global
            // tables, so switching profiles cannot leave replacement validation stale.
            val activeProfile = database.readEquipmentProfileState().active
            val equipment = activeProfile.selected
            val exclusion = database.exerciseExclusionQueries.selectById(candidate.id)
                .executeAsOneOrNull()
            val excluded = exclusion != null &&
                (exclusion.expiresAt == null || exclusion.expiresAt > nowMillis)
            if (!candidate.isAvailableWith(equipment) ||
                excluded ||
                candidate.id == original.exerciseId ||
                candidate.movementPattern.name != original.movementPattern
            ) {
                reject(OccurrenceReplacementFailure.UNAVAILABLE)
            }
            if (replacement.exerciseName != candidate.name ||
                replacement.movementPattern != candidate.movementPattern ||
                replacement.requiredEquipment != candidate.requiredEquipment ||
                replacement.involvements != candidate.effectiveInvolvements ||
                replacement.isUnilateral != candidate.isUnilateral ||
                replacement.loadCapability != candidate.loadCapability ||
                replacement.loadKind != WorkoutLoadPolicy.defaultKind(candidate.loadCapability)
            ) {
                reject(OccurrenceReplacementFailure.STALE_CONTEXT)
            }
            val weight = replacement.weightKg
            if (weight != null &&
                (
                    !weight.isFinite() ||
                        weight < 0.0 ||
                        !WorkoutLoadPolicy.allowsAutomaticLoad(candidate.loadCapability) ||
                        candidate.requiredEquipment.any { tag ->
                            val maximum = activeProfile.maxWeightsKg[tag]
                            maximum != null && weight > maximum
                        }
                    )
            ) {
                reject(OccurrenceReplacementFailure.STALE_CONTEXT)
            }
            replacement.update(queries, replacement.position)
            queries.incrementOccurrenceRevision(occurrenceId)
        }
    }

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<DomainOccurrenceEntry>
    ) = gate.mutate {
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
            .forEach { entryId ->
                queries.deleteOccurrenceEntry(entryId)
                database.entryTargetExplanationQueries.deleteForEntry(
                    OCCURRENCE_ENTRY_KIND,
                    entryId
                )
            }
    }

    override suspend fun deleteOccurrencesForActivation(activationId: Long) = gate.mutate {
        queries.transaction {
            queries.selectOccurrencesForActivation(
                activationId
            ).executeAsList().forEach { occurrence ->
                queries.selectOccurrenceEntryIdsForOccurrence(occurrence.id).executeAsList()
                    .forEach { entryId ->
                        database.entryTargetExplanationQueries.deleteForEntry(
                            OCCURRENCE_ENTRY_KIND,
                            entryId
                        )
                    }
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
            reps = reps?.toLong(),
            durationSeconds = durationSeconds?.toLong(),
            weightKg = weightKg,
            loadCapability = loadCapability.name,
            loadKind = loadKind.name,
            progressionPolicy = progressionPolicy?.let(SlotProgressionPolicyJson::encode),
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong()
        )
        val entryId = queries.lastInsertedOccurrenceEntryId().executeAsOne()
        writeEntryExplanation(OCCURRENCE_ENTRY_KIND, entryId, explanation)
        return copy(
            id = entryId,
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
            reps = reps?.toLong(),
            durationSeconds = durationSeconds?.toLong(),
            weightKg = weightKg,
            loadCapability = loadCapability.name,
            loadKind = loadKind.name,
            progressionPolicy = progressionPolicy?.let(SlotProgressionPolicyJson::encode),
            remainingDisposition = remainingDisposition?.name,
            terminalRemainingSets = terminalRemainingSets?.toLong(),
            id = id
        )
        // Always written: a null explanation clears the stored reason, so a replaced or edited slot
        // never keeps the reason of the prescription it replaced.
        writeEntryExplanation(OCCURRENCE_ENTRY_KIND, id, explanation)
    }

    private fun TrainingActivation.toDomainWithChildren(
        queries: TrainingScheduleQueries,
        explanations: Map<Long, ExerciseTargetExplanation>
    ): DomainTrainingActivation {
        val workouts = queries.selectActivationWorkoutsForActivation(id).executeAsList()
        val entries = workouts.flatMap {
            queries.selectActivationEntriesForWorkout(it.id).executeAsList()
        }
        return toDomain(workouts, entries, explanations)
    }

    private fun TrainingActivation.toDomain(
        allWorkouts: List<ActivationWorkout>,
        allEntries: List<ActivationEntry>,
        explanations: Map<Long, ExerciseTargetExplanation>
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
                        .map { it.toDomain(explanations[it.id]) }
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

    private fun ActivationEntry.toDomain(
        explanation: ExerciseTargetExplanation? = null
    ): DomainActivationEntry = DomainActivationEntry(
        id = id,
        position = position.toInt(),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        movementPattern = decodeMovementPattern(movementPattern),
        requiredEquipment = decodeEquipment(requiredEquipment),
        involvements = involvements?.let { decodeInvolvements(it) },
        isUnilateral = isUnilateral != 0L,
        sets = sets.toInt(),
        reps = reps?.toInt(),
        durationSeconds = durationSeconds?.toInt(),
        weightKg = weightKg,
        loadCapability = decodeLoadCapability(loadCapability),
        loadKind = decodeLoadKind(loadKind),
        explanation = explanation,
        progressionPolicy = progressionPolicy?.let(SlotProgressionPolicyJson::decode)
    )

    private fun WorkoutOccurrence.toDomain(
        allEntries: List<OccurrenceEntry>,
        explanations: Map<Long, ExerciseTargetExplanation>
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
            .map { it.toDomain(explanations[it.id]) }
    )

    private fun OccurrenceEntry.toDomain(
        explanation: ExerciseTargetExplanation? = null
    ): DomainOccurrenceEntry = DomainOccurrenceEntry(
        id = id,
        sourceActivationEntryId = sourceActivationEntryId, position = position.toInt(),
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        movementPattern = decodeMovementPattern(movementPattern),
        requiredEquipment = decodeEquipment(requiredEquipment),
        involvements = involvements?.let { decodeInvolvements(it) },
        isUnilateral = isUnilateral != 0L,
        sets = sets.toInt(),
        reps = reps?.toInt(),
        durationSeconds = durationSeconds?.toInt(),
        weightKg = weightKg,
        loadCapability = decodeLoadCapability(loadCapability),
        loadKind = decodeLoadKind(loadKind),
        explanation = explanation,
        progressionPolicy = progressionPolicy?.let(SlotProgressionPolicyJson::decode),
        remainingDisposition = remainingDisposition?.toRemainingDisposition(),
        terminalRemainingSets = terminalRemainingSets?.toInt()
    )

    /** The recorded reasoning per entry id for one entry kind; a row that cannot decode is dropped. */
    private fun entryExplanations(
        rows: List<EntryTargetExplanation>,
        kind: String
    ): Map<Long, ExerciseTargetExplanation> = rows
        .filter { it.entryKind == kind }
        .mapNotNull { row ->
            val explanation = row.toExplanation() ?: return@mapNotNull null
            row.entryId to explanation
        }
        .toMap()

    /**
     * Persists one entry's reasoning, or clears it. A null explanation is a real state (a replaced or
     * edited slot, or a manual routine), so it deletes any stored row rather than leaving a stale one.
     */
    private fun writeEntryExplanation(
        entryKind: String,
        entryId: Long,
        explanation: ExerciseTargetExplanation?
    ) {
        if (explanation == null) {
            database.entryTargetExplanationQueries.deleteForEntry(entryKind, entryId)
            return
        }
        val columns = explanation.toColumns()
        database.entryTargetExplanationQueries.insertExplanation(
            entryKind = entryKind,
            entryId = entryId,
            explanationVersion = columns.explanationVersion,
            baselineSource = columns.baselineSource,
            baselineRecentEstimateKg = columns.baselineRecentEstimateKg,
            baselineQualifyingSetCount = columns.baselineQualifyingSetCount,
            baselineManualFloorKg = columns.baselineManualFloorKg,
            baselineKg = columns.baselineKg,
            progressionOutcome = columns.progressionOutcome,
            progressionIncrements = columns.progressionIncrements,
            progressionDeltaKg = columns.progressionDeltaKg,
            progressionRoundToKg = columns.progressionRoundToKg,
            progressionSuccessStreakSize = columns.progressionSuccessStreakSize,
            progressionFailureStreakSize = columns.progressionFailureStreakSize,
            conversionReps = columns.conversionReps,
            conversionIntensityFraction = columns.conversionIntensityFraction,
            conversionDeloadIntensityScale = columns.conversionDeloadIntensityScale,
            conversionRoundToKg = columns.conversionRoundToKg,
            conversionUncappedWorkingKg = columns.conversionUncappedWorkingKg,
            conversionEquipmentCeilingKg = columns.conversionEquipmentCeilingKg,
            conversionEquipmentCeilingBinds = columns.conversionEquipmentCeilingBinds,
            aiProposedKg = columns.aiProposedKg,
            aiProposedAccepted = columns.aiProposedAccepted,
            aiDeloadScaled = columns.aiDeloadScaled,
            aiDeloadIntensityScale = columns.aiDeloadIntensityScale,
            aiProgressionBoundKg = columns.aiProgressionBoundKg,
            aiBoundedByProgression = columns.aiBoundedByProgression,
            aiEquipmentCeilingKg = columns.aiEquipmentCeilingKg,
            aiCappedByEquipment = columns.aiCappedByEquipment,
            setBaseSets = columns.setBaseSets,
            setDeloadApplied = columns.setDeloadApplied,
            setDeloadVolumeScale = columns.setDeloadVolumeScale,
            setFatigueEffect = columns.setFatigueEffect,
            setFinalSets = columns.setFinalSets,
            unavailableReason = columns.unavailableReason
        )
    }

    private fun EntryTargetExplanation.toExplanation(): ExerciseTargetExplanation? =
        decodeTargetExplanation(
            explanationVersion = explanationVersion,
            baselineSource = baselineSource,
            baselineRecentEstimateKg = baselineRecentEstimateKg,
            baselineQualifyingSetCount = baselineQualifyingSetCount,
            baselineManualFloorKg = baselineManualFloorKg,
            baselineKg = baselineKg,
            progressionOutcome = progressionOutcome,
            progressionIncrements = progressionIncrements,
            progressionDeltaKg = progressionDeltaKg,
            progressionRoundToKg = progressionRoundToKg,
            progressionSuccessStreakSize = progressionSuccessStreakSize,
            progressionFailureStreakSize = progressionFailureStreakSize,
            conversionReps = conversionReps,
            conversionIntensityFraction = conversionIntensityFraction,
            conversionDeloadIntensityScale = conversionDeloadIntensityScale,
            conversionRoundToKg = conversionRoundToKg,
            conversionUncappedWorkingKg = conversionUncappedWorkingKg,
            conversionEquipmentCeilingKg = conversionEquipmentCeilingKg,
            conversionEquipmentCeilingBinds = conversionEquipmentCeilingBinds,
            aiProposedKg = aiProposedKg,
            aiProposedAccepted = aiProposedAccepted,
            aiDeloadScaled = aiDeloadScaled,
            aiDeloadIntensityScale = aiDeloadIntensityScale,
            aiProgressionBoundKg = aiProgressionBoundKg,
            aiBoundedByProgression = aiBoundedByProgression,
            aiEquipmentCeilingKg = aiEquipmentCeilingKg,
            aiCappedByEquipment = aiCappedByEquipment,
            setBaseSets = setBaseSets,
            setDeloadApplied = setDeloadApplied,
            setDeloadVolumeScale = setDeloadVolumeScale,
            setFatigueEffect = setFatigueEffect,
            setFinalSets = setFinalSets,
            unavailableReason = unavailableReason
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
        /** Entry kinds for [entryTargetExplanation], which stores a reason per entry row. */
        const val ACTIVATION_ENTRY_KIND = "ACTIVATION"
        const val OCCURRENCE_ENTRY_KIND = "OCCURRENCE"

        val RESOLVED_STATUS_NAMES: Set<String> = setOf(
            OccurrenceStatus.FINISHED.name,
            OccurrenceStatus.FINISHED_PARTIAL.name,
            OccurrenceStatus.SKIPPED.name
        )
    }
}
