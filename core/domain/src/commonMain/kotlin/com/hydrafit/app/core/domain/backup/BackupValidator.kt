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

/**
 * Validates a whole payload before any write. Rejection is all-or-nothing: an unknown catalog id, a
 * duplicate identity, a dangling link, an illegal enum or a non-finite number fails the whole file.
 * Catalog definitions/aliases are never imported, so a custom id may not occupy a seed identity and a
 * CAT-P7-named custom stays separate from its same-named seed.
 */
class BackupValidator(private val catalog: BackupCatalog) {

    fun validate(file: BackupFile) {
        if (file.format != BACKUP_FORMAT || file.formatVersion != BACKUP_FORMAT_VERSION) {
            fail(BackupFailure.UNSUPPORTED_VERSION)
        }

        val seedExerciseIds = catalog.seedExerciseIds()
        val builtInEquipmentIds = catalog.builtInEquipmentIds()

        val customIds = uniqueIds(file.customExercises) { it.id }
        customIds.forEach { id ->
            if (id in seedExerciseIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }

        val planIds = uniqueIds(file.plans) { it.id }
        val dayIds = uniqueIds(file.planDays) { it.id }
        uniqueIds(file.planEntries) { it.id }
        val routineIds = uniqueIds(file.routines) { it.id }
        val routineWorkoutIds = uniqueIds(file.routineWorkouts) { it.id }
        uniqueIds(file.routineEntries) { it.id }
        val activationIds = uniqueIds(file.activations) { it.id }
        val activationWorkoutIds = uniqueIds(file.activationWorkouts) { it.id }
        val activationEntryIds = uniqueIds(file.activationEntries) { it.id }
        val occurrenceIds = uniqueIds(file.occurrences) { it.id }
        val occurrenceEntryIds = uniqueIds(file.occurrenceEntries) { it.id }
        uniqueIds(file.workoutSets) { it.id }

        val customEquipmentIds = uniqueIds(
            file.equipment.filter { !it.isBuiltIn }
        ) { it.id }
        val knownEquipment = builtInEquipmentIds + customEquipmentIds
        val knownExercises = seedExerciseIds + customIds

        file.customExercises.forEach {
            enum(it.movementPattern, MovementPattern.entries)
            enum(it.loadCapability, ExerciseLoadCapability.entries)
            equipment(it.requiredEquipment, knownEquipment)
        }
        file.exerciseOverrides.forEach {
            it.movementPattern?.let { value -> enum(value, MovementPattern.entries) }
            it.loadCapability?.let { value -> enum(value, ExerciseLoadCapability.entries) }
            it.requiredEquipment?.let { value -> equipment(value, knownEquipment) }
            exercise(it.exerciseId, knownExercises)
        }
        file.equipment.forEach { it.maxWeightKg?.let { value -> finite(value) } }

        file.workoutSets.forEach {
            exercise(it.exerciseId, knownExercises)
            enum(it.loadKind, LoadKind.entries)
            it.weightKg?.let { value -> finite(value) }
            it.occurrenceId?.let { id -> reference(id, occurrenceIds) }
            it.occurrenceEntryId?.let { id -> reference(id, occurrenceEntryIds) }
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

        file.routineWorkouts.forEach { reference(it.templateId, routineIds) }
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
        file.activationWorkouts.forEach { reference(it.activationId, activationIds) }
        file.activationEntries.forEach { entry ->
            reference(entry.workoutId, activationWorkoutIds)
            exercise(entry.exerciseId, knownExercises)
            enum(entry.movementPattern, MovementPattern.entries)
            enum(entry.loadCapability, ExerciseLoadCapability.entries)
            enum(entry.loadKind, LoadKind.entries)
            equipment(entry.requiredEquipment, knownEquipment)
            entry.weightKg?.let { value -> finite(value) }
        }
        file.occurrences.forEach {
            reference(it.activationId, activationIds)
            reference(it.activationWorkoutId, activationWorkoutIds)
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

    private fun <T, K> uniqueIds(items: List<T>, id: (T) -> K): Set<K> {
        val seen = mutableSetOf<K>()
        items.forEach { if (!seen.add(id(it))) fail(BackupFailure.DUPLICATE_ID) }
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

    private fun reference(id: Long, known: Set<Long>) {
        if (id !in known) fail(BackupFailure.INVALID_REFERENCE)
    }

    private fun finite(value: Double) {
        if (!value.isFinite()) fail(BackupFailure.INVALID_VALUE)
    }

    private fun <E : Enum<E>> enum(value: String?, values: List<E>) {
        if (value != null && values.none { it.name == value }) fail(BackupFailure.INVALID_VALUE)
    }

    private fun fail(failure: BackupFailure): Nothing = throw BackupException(failure)
}
