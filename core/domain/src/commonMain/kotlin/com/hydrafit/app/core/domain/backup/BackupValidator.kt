package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance

/**
 * Validates a whole payload before any write. Rejection is all-or-nothing: an unknown catalog id, a
 * duplicate identity (including a duplicate composite key), a dangling link, an illegal enum, a
 * non-finite number or an unsupported involvement encoding fails the whole file.
 * Catalog definitions/aliases are never imported, so a custom id may not occupy a seed identity and a
 * CAT-P7-named custom stays separate from its same-named seed.
 */
class BackupValidator(private val catalog: BackupCatalog) {

    fun validate(file: BackupFile) {
        if (file.format != BACKUP_FORMAT || file.formatVersion !in SUPPORTED_FORMAT_VERSIONS) {
            fail(BackupFailure.UNSUPPORTED_VERSION)
        }

        val seedExerciseIds = catalog.seedExerciseIds()
        val builtInEquipmentIds = catalog.builtInEquipmentIds()

        val customIds = uniqueBy(file.customExercises) { it.id }
        customIds.forEach { id ->
            if (id in seedExerciseIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }

        val planIds = uniqueBy(file.plans) { it.id }
        val dayIds = uniqueBy(file.planDays) { it.id }
        uniqueBy(file.planEntries) { it.id }
        val routineIds = uniqueBy(file.routines) { it.id }
        val routineWorkoutIds = uniqueBy(file.routineWorkouts) { it.id }
        uniqueBy(file.routineEntries) { it.id }
        val activationIds = uniqueBy(file.activations) { it.id }
        val activationWorkoutIds = uniqueBy(file.activationWorkouts) { it.id }
        val activationEntryIds = uniqueBy(file.activationEntries) { it.id }
        val occurrenceIds = uniqueBy(file.occurrences) { it.id }
        val occurrenceEntryIds = uniqueBy(file.occurrenceEntries) { it.id }
        val sessionIds = uniqueBy(file.workoutSessions) { it.id }
        uniqueBy(file.workoutSets) { it.id }

        // Natural keys that must not repeat, because restore addresses rows by them.
        uniqueBy(file.equipment) { it.id }
        uniqueBy(file.exerciseOverrides) { it.exerciseId }
        uniqueBy(file.personalRecords) { it.exerciseId }
        uniqueBy(file.preferences) { it.exerciseId }
        uniqueBy(file.exclusions) { it.exerciseId }
        uniqueBy(file.volumeExplanations) { it.planId to it.muscle }
        uniqueBy(file.volumeExplanationStates) { it.planId }
        uniqueBy(file.selectedEquipment) { it }

        val customEquipmentIds = file.equipment.filter { !it.isBuiltIn }.map { it.id }.toSet()
        customEquipmentIds.forEach { id ->
            if (id in builtInEquipmentIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }
        file.equipment.filter { it.isBuiltIn }.forEach { row ->
            if (row.id !in builtInEquipmentIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }
        val knownEquipment = builtInEquipmentIds + customEquipmentIds
        val knownExercises = seedExerciseIds + customIds
        val dedupeSeedKeys = catalog.dedupeSeedKeys()

        // Catalog compatibility: every seed the file relies on must exist here with the same profile.
        uniqueBy(file.catalog.seedProfiles) { it.id }
        val receiverProfiles = catalog.seedProfiles()
        file.catalog.seedProfiles.forEach { exported ->
            val actual = receiverProfiles[exported.id] ?: fail(BackupFailure.CATALOG_MISMATCH)
            if (actual != exported.profile) fail(BackupFailure.CATALOG_MISMATCH)
        }

        val workoutToActivation = file.activationWorkouts.associate { it.id to it.activationId }
        val occurrenceEntryToOccurrence =
            file.occurrenceEntries.associate { it.id to it.occurrenceId }

        file.selectedEquipment.forEach { equipment(it, knownEquipment) }

        file.customExercises.forEach {
            enum(it.movementPattern, MovementPattern.entries)
            enum(it.loadCapability, ExerciseLoadCapability.entries)
            equipment(it.requiredEquipment, knownEquipment)
            involvements(it.involvements)
            if (catalog.dedupeNameKey(it.name) in dedupeSeedKeys) {
                fail(BackupFailure.STARTUP_UNSTABLE)
            }
        }
        file.exerciseOverrides.forEach {
            it.movementPattern?.let { value -> enum(value, MovementPattern.entries) }
            it.loadCapability?.let { value -> enum(value, ExerciseLoadCapability.entries) }
            it.requiredEquipment?.let { value -> equipment(value, knownEquipment) }
            exercise(it.exerciseId, knownExercises)
            involvements(it.involvements)
        }
        file.equipment.forEach { it.maxWeightKg?.let { value -> finite(value) } }

        file.workoutSets.forEach {
            exercise(it.exerciseId, knownExercises)
            enum(it.loadKind, LoadKind.entries)
            enum(it.timingProvenance, WorkoutTimingProvenance.entries)
            it.weightKg?.let { value -> finite(value) }
            it.occurrenceId?.let { id -> reference(id, occurrenceIds) }
            it.occurrenceEntryId?.let { id -> reference(id, occurrenceEntryIds) }
            it.sessionId?.let { id -> reference(id, sessionIds) }
            if (it.sessionId == null) fail(BackupFailure.STARTUP_UNSTABLE)
            involvements(it.involvements)
            if (it.occurrenceId != null &&
                it.occurrenceEntryId != null &&
                occurrenceEntryToOccurrence[it.occurrenceEntryId] != it.occurrenceId
            ) {
                fail(BackupFailure.INVALID_REFERENCE)
            }
        }
        file.workoutSessions.forEach {
            it.occurrenceId?.let { id -> reference(id, occurrenceIds) }
        }

        file.planDays.forEach { day ->
            reference(day.planId, planIds)
            enum(day.focus, SplitFocus.entries)
        }
        file.planEntries.forEach { entry ->
            reference(entry.dayId, dayIds)
            exercise(entry.exerciseId, knownExercises)
            enum(entry.movementPattern, MovementPattern.entries)
            enum(entry.loadCapability, ExerciseLoadCapability.entries)
            enum(entry.loadKind, LoadKind.entries)
            entry.suggestedWeightKg?.let { value -> finite(value) }
        }
        file.volumeExplanations.forEach {
            reference(it.planId, planIds)
            enum(it.muscle, MuscleGroup.entries)
            enum(it.attribution, PlanAttribution.entries)
            finite(it.estimatedOtherInvolvementCredits)
        }
        file.volumeExplanationStates.forEach {
            reference(it.planId, planIds)
            enum(it.status, VolumeExplanationStatus.entries)
        }

        file.routineWorkouts.forEach {
            reference(it.templateId, routineIds)
            it.focus?.let { value -> enum(value, SplitFocus.entries) }
        }
        file.routineEntries.forEach { entry ->
            reference(entry.workoutId, routineWorkoutIds)
            exercise(entry.exerciseId, knownExercises)
            enum(entry.loadKind, LoadKind.entries)
            entry.weightKg?.let { value -> finite(value) }
        }
        file.activations.forEach {
            enum(it.mode, ScheduleMode.entries)
            enum(it.status, ActivationStatus.entries)
        }
        file.activationWorkouts.forEach {
            reference(it.activationId, activationIds)
            it.focus?.let { value -> enum(value, SplitFocus.entries) }
        }
        file.activationEntries.forEach { entry ->
            reference(entry.workoutId, activationWorkoutIds)
            exercise(entry.exerciseId, knownExercises)
            enum(entry.movementPattern, MovementPattern.entries)
            enum(entry.loadCapability, ExerciseLoadCapability.entries)
            enum(entry.loadKind, LoadKind.entries)
            equipment(entry.requiredEquipment, knownEquipment)
            entry.weightKg?.let { value -> finite(value) }
            involvements(entry.involvements)
        }
        file.occurrences.forEach {
            reference(it.activationId, activationIds)
            reference(it.activationWorkoutId, activationWorkoutIds)
            if (workoutToActivation[it.activationWorkoutId] != it.activationId) {
                fail(BackupFailure.INVALID_REFERENCE)
            }
            enum(it.status, OccurrenceStatus.entries)
        }
        file.occurrenceEntries.forEach { entry ->
            reference(entry.occurrenceId, occurrenceIds)
            entry.sourceActivationEntryId?.let { id -> reference(id, activationEntryIds) }
            exercise(entry.exerciseId, knownExercises)
            enum(entry.movementPattern, MovementPattern.entries)
            enum(entry.loadCapability, ExerciseLoadCapability.entries)
            enum(entry.loadKind, LoadKind.entries)
            enum(entry.remainingDisposition, RemainingDisposition.entries)
            equipment(entry.requiredEquipment, knownEquipment)
            entry.weightKg?.let { value -> finite(value) }
            involvements(entry.involvements)
        }
        file.scheduleState?.let { state ->
            state.activeActivationId?.let { id -> reference(id, activationIds) }
            state.selectedOccurrenceId?.let { id -> reference(id, occurrenceIds) }
        }

        file.personalRecords.forEach {
            exercise(it.exerciseId, knownExercises)
            enum(it.loadKind, LoadKind.entries)
            finite(it.weightKg)
        }
        file.preferences.forEach {
            exercise(it.exerciseId, knownExercises)
            enum(it.preference, ExercisePreference.entries)
        }
        file.exclusions.forEach { exercise(it.exerciseId, knownExercises) }

        file.settings?.let { settings ->
            enum(settings.engineId, PlannerEngineId.entries)
            enum(settings.trainingGoal, TrainingGoal.entries)
            enum(settings.weightUnit, WeightUnit.entries)
        }
    }

    private fun <T, K> uniqueBy(items: List<T>, key: (T) -> K): Set<K> {
        val seen = mutableSetOf<K>()
        items.forEach { if (!seen.add(key(it))) fail(BackupFailure.DUPLICATE_ID) }
        return seen
    }

    private fun exercise(id: String, known: Set<String>) {
        if (id !in known) fail(BackupFailure.UNKNOWN_CATALOG_ID)
    }

    private fun equipment(csv: String, known: Set<String>) {
        csv.split(',')
            .filter { it.isNotEmpty() }
            .forEach { token -> if (token !in known) fail(BackupFailure.UNKNOWN_CATALOG_ID) }
    }

    /**
     * Rejects an involvement string whose tokens are not known muscles (or the pre-MUS-P1 broad names
     * still present in historical snapshots) or whose weights are missing or non-finite. `null` and
     * `""` are both valid and distinct: they mean "not set" and "explicitly cleared".
     */
    private fun involvements(csv: String?) {
        if (csv.isNullOrEmpty()) return
        csv.split(',').forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) fail(BackupFailure.INVALID_VALUE)
            if (parts[0] !in muscleTokens) fail(BackupFailure.INVALID_VALUE)
            val weight = parts[1].toDoubleOrNull()
            if (weight == null || !weight.isFinite()) fail(BackupFailure.INVALID_VALUE)
        }
    }

    private fun reference(id: Long, known: Set<Long>) {
        if (id !in known) fail(BackupFailure.INVALID_REFERENCE)
    }

    private fun reference(id: String, known: Set<String>) {
        if (id !in known) fail(BackupFailure.INVALID_REFERENCE)
    }

    private fun finite(value: Double) {
        if (!value.isFinite()) fail(BackupFailure.INVALID_VALUE)
    }

    private fun <E : Enum<E>> enum(value: String?, values: List<E>) {
        if (value != null && values.none { it.name == value }) fail(BackupFailure.INVALID_VALUE)
    }

    private fun fail(failure: BackupFailure): Nothing = throw BackupException(failure)

    private companion object {
        val SUPPORTED_FORMAT_VERSIONS = 1..BACKUP_FORMAT_VERSION

        /** Muscle names accepted in an involvement snapshot, including the legacy broad names. */
        val muscleTokens: Set<String> =
            MuscleGroup.entries.map { it.name }.toSet() +
                setOf("CHEST", "BACK", "SHOULDERS", "CORE")
    }
}
