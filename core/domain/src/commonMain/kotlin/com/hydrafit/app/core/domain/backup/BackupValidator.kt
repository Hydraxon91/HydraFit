package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.FrozenPlanExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.ProgressionOutcome
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
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
import com.hydrafit.app.core.domain.setup.SETUP_VERSION
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportPartition
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance

/**
 * Validates a whole payload before any write. Rejection is all-or-nothing: an unknown catalog id, a
 * duplicate identity (including a duplicate composite key), a dangling link, an illegal enum, a
 * non-finite number or an unsupported involvement encoding fails the whole file.
 * Catalog definitions/aliases are never imported, so a custom id may not occupy a seed identity and a
 * CAT-P7-named custom stays separate from its same-named seed.
 */
class BackupValidator(private val catalog: BackupCatalog) {

    private fun checkSupersets(entries: List<Pair<String, String?>>) {
        try {
            com.hydrafit.app.core.domain.routine.validateSupersets(
                entries.map { (id, encoded) ->
                    id to encoded?.let(com.hydrafit.app.core.domain.routine.SupersetJson::decode)
                }
            )
        } catch (_: IllegalArgumentException) {
            fail(BackupFailure.INVALID_VALUE)
        }
    }

    fun prepareForRestore(file: BackupFile): BackupFile =
        file.withNonConflictingEquipment(catalog.builtInEquipmentIds()).also { validate(it) }

    fun validate(file: BackupFile) {
        if (file.format != BACKUP_FORMAT || file.formatVersion !in SUPPORTED_FORMAT_VERSIONS) {
            fail(BackupFailure.UNSUPPORTED_VERSION)
        }
        if (file.formatVersion < EXERCISE_NOTES_FORMAT_VERSION && file.exerciseNotes.isNotEmpty()) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (file.formatVersion < SESSION_NOTES_FORMAT_VERSION && file.sessionNotes.isNotEmpty()) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (file.formatVersion < WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION &&
            file.warmupPreparationOverrides.isNotEmpty()
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        // A slot progression policy does not exist before format 22, so its presence in an older file
        // is a signal the file is not what it claims rather than a rule to adopt. A policy that is
        // present must decode, so a corrupt rule is rejected before any write.
        val policies = file.routineEntries.map { it.progressionPolicy } +
            file.planEntries.map { it.progressionPolicy } +
            file.activationEntries.map { it.progressionPolicy } +
            file.occurrenceEntries.map { it.progressionPolicy }
        if (file.formatVersion < PROGRESSION_POLICY_FORMAT_VERSION && policies.any { it != null }) {
            fail(BackupFailure.INVALID_VALUE)
        }
        policies.filterNotNull().forEach { encoded ->
            if (runCatching { SlotProgressionPolicyJson.decode(encoded) }.isFailure) {
                fail(BackupFailure.INVALID_VALUE)
            }
        }
        // Ordered per-set targets arrive with format 23. A pre-23 file that carries them is not what
        // it claims, and a present list must decode so a corrupt prescription is rejected before any
        // write rather than becoming an approved plan.
        val orderedTargets = file.routineEntries.map { it.setTargets } +
            file.planEntries.map { it.setTargets } +
            file.activationEntries.map { it.setTargets } +
            file.occurrenceEntries.map { it.setTargets }
        if (file.formatVersion < SET_TARGETS_FORMAT_VERSION && orderedTargets.any { it != null }) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.routineEntries.forEach { checkOrderedTargets(it.sets, it.setTargets) }
        file.planEntries.forEach { checkOrderedTargets(it.sets, it.setTargets) }
        file.activationEntries.forEach { checkOrderedTargets(it.sets, it.setTargets) }
        file.occurrenceEntries.forEach { checkOrderedTargets(it.sets, it.setTargets) }
        val supersetValues = file.routineEntries.map { it.superset } +
            file.planEntries.map { it.superset } +
            file.activationEntries.map { it.superset } +
            file.occurrenceEntries.map { it.superset }
        if (file.formatVersion < SUPERSET_FORMAT_VERSION && supersetValues.any { it != null }) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.routineEntries.groupBy { it.workoutId }.values.forEach { entries ->
            checkSupersets(entries.sortedBy { it.position }.map { it.exerciseId to it.superset })
        }
        file.planEntries.groupBy { it.dayId }.values.forEach { entries ->
            checkSupersets(entries.sortedBy { it.position }.map { it.exerciseId to it.superset })
        }
        file.activationEntries.groupBy { it.workoutId }.values.forEach { entries ->
            checkSupersets(entries.sortedBy { it.position }.map { it.exerciseId to it.superset })
        }
        file.occurrenceEntries.groupBy { it.occurrenceId }.values.forEach { entries ->
            checkSupersets(entries.sortedBy { it.position }.map { it.exerciseId to it.superset })
        }
        if (file.formatVersion < SETUP_FORMAT_VERSION &&
            (file.setup != BackupSetupRecord() || file.preferredWeekdays != null)
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (file.formatVersion < REMINDER_FORMAT_VERSION && file.workoutReminders != null) {
            fail(BackupFailure.INVALID_VALUE)
        }
        // The rest-presentation opt-ins are part of the same format boundary. A pre-12 file that
        // carries either non-default value is rejected all-or-nothing rather than silently
        // normalized: the fields do not exist in that version, so their presence is a signal the
        // file is not what it claims. Missing or false fields remain the legacy "off" compatibility.
        if (file.formatVersion < REMINDER_FORMAT_VERSION &&
            file.trainingGuidance.restCountdownEnabled
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (file.formatVersion < REMINDER_FORMAT_VERSION &&
            file.trainingGuidance.restCompletionSignalEnabled
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (file.setup.version != SETUP_VERSION) fail(BackupFailure.INVALID_VALUE)
        enum(file.setup.disposition, SetupDisposition.entries)
        enum(file.setup.step, SetupStep.entries)
        file.preferredWeekdays?.let { weekdays ->
            if (weekdays.size !in 2..6 || weekdays.distinct().size != weekdays.size) {
                fail(BackupFailure.INVALID_VALUE)
            }
            weekdays.forEach { enum(it, DayOfWeek.entries) }
        }

        // Body weight is whole-replaced; a legacy file carrying either section is not what it claims.
        if (file.formatVersion < BODY_WEIGHT_FORMAT_VERSION &&
            (file.bodyWeightMeasurements.isNotEmpty() || file.bodyWeightGoalKg != null)
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        val measurementDays = file.bodyWeightMeasurements.map { it.localEpochDay }
        if (measurementDays.distinct().size != measurementDays.size) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.bodyWeightMeasurements.forEach {
            if (!it.weightKg.isFinite() || it.weightKg <= 0.0) fail(BackupFailure.INVALID_VALUE)
        }
        file.bodyWeightGoalKg?.let {
            if (!it.isFinite() || it <= 0.0) fail(BackupFailure.INVALID_VALUE)
        }

        // Import provenance is whole-replaced, so a file from before format 15 that carries records is
        // not what it claims. Records name a source workout, not a session: an entry whose imported
        // session is absent stays valid, because that is exactly the deleted-history guard.
        if (file.formatVersion < IMPORT_PROVENANCE_FORMAT_VERSION &&
            file.importProvenance.isNotEmpty()
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.importProvenance.forEach {
            enum(it.source, WorkoutCsvSource.entries)
            if (it.candidateKey.isEmpty() ||
                it.content.isEmpty() ||
                it.importedSessionId.isEmpty()
            ) {
                fail(BackupFailure.INVALID_VALUE)
            }
        }
        uniqueBy(file.importProvenance) { Triple(it.source, it.candidateKey, it.content) }

        // The bucket/partition ledger is whole-replaced too (format 16). It permits an imported
        // bucket to keep its exact approved split, including two sessions with identical content,
        // which the per-session provenance above cannot represent. Its links survive local deletion
        // for the same reason provenance does.
        if (file.formatVersion < IMPORT_BUCKET_FORMAT_VERSION &&
            file.importBuckets.isNotEmpty()
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.importBuckets.forEach { bucket ->
            enum(bucket.source, WorkoutCsvSource.entries)
            if (bucket.candidateKey.isEmpty() ||
                bucket.content.isEmpty() ||
                bucket.partition.isEmpty() ||
                bucket.importedSessionIds.isEmpty() ||
                bucket.importedSessionIds.any { it.isEmpty() }
            ) {
                fail(BackupFailure.INVALID_VALUE)
            }
            if (bucket.importedSessionIds.distinct().size != bucket.importedSessionIds.size) {
                fail(BackupFailure.INVALID_VALUE)
            }
            // A manifest that cannot be read back, or whose session count disagrees with the links
            // restored beside it, would be reused as if it were the approved partition. Refuse it here
            // rather than let the import ledger carry a record it can never match again.
            val partition = WorkoutImportPartition.decode(bucket.partition)
                ?: fail(BackupFailure.INVALID_VALUE)
            if (partition.sessions.size != bucket.importedSessionIds.size) {
                fail(BackupFailure.INVALID_VALUE)
            }
        }
        uniqueBy(file.importBuckets) { it.source to it.candidateKey }

        val seedExerciseIds = catalog.seedExerciseIds()
        val builtInEquipmentIds = catalog.builtInEquipmentIds()

        val customIds = uniqueBy(file.customExercises) { it.id }
        customIds.forEach { id ->
            if (id in seedExerciseIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }

        val planIds = uniqueBy(file.plans) { it.id }
        val dayIds = uniqueBy(file.planDays) { it.id }
        uniqueBy(file.planEntries) { it.id }
        val planDaysById = file.planDays.associateBy { it.id }
        val planSlots = file.planEntries.mapNotNull { entry ->
            planDaysById[entry.dayId]?.let { day ->
                Triple(day.planId, day.dayIndex, entry.position)
            }
        }.toSet()
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
        uniqueBy(file.restPreferences) { it.exerciseId }
        uniqueBy(file.volumeExplanations) { it.planId to it.muscle }
        uniqueBy(file.volumeExplanationStates) { it.planId }
        uniqueBy(file.planTargetExplanations) { Triple(it.planId, it.dayIndex, it.position) }
        uniqueBy(file.planTargetExplanationStates) { it.planId }
        uniqueBy(file.entryTargetExplanations) { it.entryKind to it.entryId }
        uniqueBy(file.exerciseNotes) { it.exerciseId }
        uniqueBy(file.sessionNotes) { it.sessionId }
        uniqueBy(file.warmupPreparationOverrides) { it.action }
        uniqueBy(file.selectedEquipment) { it }

        val customEquipmentIds = file.equipment.filter { !it.isBuiltIn }.map { it.id }.toSet()
        customEquipmentIds.forEach { id ->
            if (id in builtInEquipmentIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }
        file.equipment.filter { it.isBuiltIn }.forEach { row ->
            if (row.id !in builtInEquipmentIds) fail(BackupFailure.UNKNOWN_CATALOG_ID)
        }
        val knownEquipment = builtInEquipmentIds + customEquipmentIds
        validateEquipmentProfiles(file, knownEquipment)
        val knownExercises = seedExerciseIds + customIds
        val dedupeSeedKeys = catalog.dedupeSeedKeys()

        // Compatibility exceptions are exact application-owned historical profiles, not imported rules.
        uniqueBy(file.catalog.seedProfiles) { it.id }
        val receiverProfiles = catalog.seedProfiles()
        val previousProfiles = catalog.compatiblePreviousSeedProfiles()
        file.catalog.seedProfiles.forEach { exported ->
            val actual = receiverProfiles[exported.id] ?: fail(BackupFailure.CATALOG_MISMATCH)
            if (
                actual != exported.profile &&
                exported.profile !in previousProfiles[exported.id].orEmpty()
            ) {
                fail(BackupFailure.CATALOG_MISMATCH)
            }
        }

        val workoutToActivation = file.activationWorkouts.associate { it.id to it.activationId }
        val occurrenceEntryToOccurrence =
            file.occurrenceEntries.associate { it.id to it.occurrenceId }

        file.selectedEquipment.forEach { equipment(it, knownEquipment) }

        if (file.formatVersion >= 5 && file.restPreferences.count { it.exerciseId == null } != 1) {
            fail(BackupFailure.INVALID_VALUE)
        }
        file.restPreferences.forEach { preference ->
            if (preference.durationSeconds !in 1L..86_400L) fail(BackupFailure.INVALID_VALUE)
            preference.exerciseId?.let { exercise(it, knownExercises) }
        }
        file.workoutReminders?.let { reminders ->
            reminders.reminderMinuteOfDay?.let { minute ->
                if (minute !in MINUTE_OF_DAY_RANGE) fail(BackupFailure.INVALID_VALUE)
            }
            if (reminders.quietStartMinuteOfDay !in MINUTE_OF_DAY_RANGE ||
                reminders.quietEndMinuteOfDay !in MINUTE_OF_DAY_RANGE
            ) {
                fail(BackupFailure.INVALID_VALUE)
            }
        }

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
            // A recorded target does not exist before format 21, so its presence in an older file is
            // a signal the file is not what it claims rather than a value to reinterpret.
            if (file.formatVersion < PRESCRIPTION_SNAPSHOT_FORMAT_VERSION &&
                it.prescriptionSnapshot != null
            ) {
                fail(BackupFailure.INVALID_VALUE)
            }
            it.prescriptionSnapshot?.let { encoded ->
                prescriptionSnapshot(encoded, it, file.formatVersion)
            }
            // A set is measured by positive reps or positive whole seconds, never both and never
            // neither. A duration in a format that predates the field is rejected instead of being
            // reinterpreted as a rep count.
            if (file.formatVersion < WORKOUT_SET_METRICS_FORMAT_VERSION &&
                it.durationSeconds != null
            ) {
                fail(BackupFailure.INVALID_VALUE)
            }
            val hasReps = it.reps != null && it.reps > 0
            val hasDuration = it.durationSeconds != null && it.durationSeconds > 0
            if (hasReps == hasDuration) fail(BackupFailure.INVALID_VALUE)
            if (it.reps != null && it.reps <= 0) fail(BackupFailure.INVALID_VALUE)
            it.durationSeconds?.let { seconds ->
                if (seconds <= 0) fail(BackupFailure.INVALID_VALUE)
            }
            if ((it.startedAtElapsedMillis == null) != (it.completedAtElapsedMillis == null)) {
                fail(BackupFailure.INVALID_VALUE)
            }
            it.startedAtElapsedMillis?.let { startedAt ->
                if (startedAt < 0L || it.completedAtElapsedMillis!! < startedAt) {
                    fail(BackupFailure.INVALID_VALUE)
                }
            }
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
            prescriptionMetric(
                file.formatVersion,
                entry.reps,
                entry.durationSeconds,
                PLAN_ENTRY_DURATION_FORMAT_VERSION
            )
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
        val targetReasonPlans = file.planTargetExplanationStates.map { it.planId }.toSet()
        if (file.planTargetExplanations.any { it.planId !in targetReasonPlans }) {
            // A slot reason without its plan-level context would restore an inconsistent plan.
            fail(BackupFailure.INVALID_REFERENCE)
        }
        file.planTargetExplanations.forEach {
            reference(it.planId, planIds)
            if (Triple(it.planId, it.dayIndex, it.position) !in planSlots) {
                fail(BackupFailure.INVALID_REFERENCE)
            }
            if (it.explanationVersion != FrozenPlanExplanation.EXPLANATION_VERSION) {
                fail(BackupFailure.INVALID_VALUE)
            }
            enum(it.baselineSource, LoadBaselineSource.entries)
            enum(it.progressionOutcome, ProgressionOutcome.entries)
            enum(it.setFatigueEffect, FatigueSetEffect.entries)
            enum(it.unavailableReason, LoadUnavailableReason.entries)
            listOf(
                it.baselineRecentEstimateKg,
                it.baselineManualFloorKg,
                it.baselineKg,
                it.progressionDeltaKg,
                it.progressionRoundToKg,
                it.conversionIntensityFraction,
                it.conversionDeloadIntensityScale,
                it.conversionRoundToKg,
                it.conversionUncappedWorkingKg,
                it.conversionEquipmentCeilingKg,
                it.aiProposedKg,
                it.aiDeloadIntensityScale,
                it.aiProgressionBoundKg,
                it.aiEquipmentCeilingKg,
                it.setDeloadVolumeScale
            ).forEach { value -> value?.let { finite(it) } }
        }
        file.planTargetExplanationStates.forEach {
            reference(it.planId, planIds)
            if (it.explanationVersion != FrozenPlanExplanation.EXPLANATION_VERSION) {
                fail(BackupFailure.INVALID_VALUE)
            }
            enum(it.attribution, PlanAttribution.entries)
            finite(it.deloadVolumeScale)
            finite(it.deloadIntensityScale)
        }
        file.entryTargetExplanations.forEach {
            if (it.explanationVersion != FrozenPlanExplanation.EXPLANATION_VERSION) {
                fail(BackupFailure.INVALID_VALUE)
            }
            when (it.entryKind) {
                ENTRY_KIND_ACTIVATION -> reference(it.entryId, activationEntryIds)
                ENTRY_KIND_OCCURRENCE -> reference(it.entryId, occurrenceEntryIds)
                else -> fail(BackupFailure.INVALID_VALUE)
            }
            enum(it.baselineSource, LoadBaselineSource.entries)
            enum(it.progressionOutcome, ProgressionOutcome.entries)
            enum(it.setFatigueEffect, FatigueSetEffect.entries)
            enum(it.unavailableReason, LoadUnavailableReason.entries)
            listOf(
                it.baselineRecentEstimateKg,
                it.baselineManualFloorKg,
                it.baselineKg,
                it.progressionDeltaKg,
                it.progressionRoundToKg,
                it.conversionIntensityFraction,
                it.conversionDeloadIntensityScale,
                it.conversionRoundToKg,
                it.conversionUncappedWorkingKg,
                it.conversionEquipmentCeilingKg,
                it.aiProposedKg,
                it.aiDeloadIntensityScale,
                it.aiProgressionBoundKg,
                it.aiEquipmentCeilingKg,
                it.setDeloadVolumeScale
            ).forEach { value -> value?.let { finite(it) } }
        }

        file.routineWorkouts.forEach {
            reference(it.templateId, routineIds)
            it.focus?.let { value -> enum(value, SplitFocus.entries) }
        }
        file.routineEntries.forEach { entry ->
            reference(entry.workoutId, routineWorkoutIds)
            exercise(entry.exerciseId, knownExercises)
            enum(entry.loadKind, LoadKind.entries)
            prescriptionMetric(file.formatVersion, entry.reps, entry.durationSeconds)
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
            prescriptionMetric(file.formatVersion, entry.reps, entry.durationSeconds)
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
            prescriptionMetric(file.formatVersion, entry.reps, entry.durationSeconds)
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
        file.exerciseNotes.forEach {
            exercise(it.exerciseId, knownExercises)
            if (it.note.length > NOTE_MAX_LENGTH) fail(BackupFailure.INVALID_VALUE)
        }
        file.sessionNotes.forEach {
            reference(it.sessionId, sessionIds)
            if (it.note.length > NOTE_MAX_LENGTH) fail(BackupFailure.INVALID_VALUE)
        }
        file.warmupPreparationOverrides.forEach {
            // An unknown action name is rejected rather than silently dropped, and the measure must be
            // exactly one positive value, mirroring every other prescription slot.
            enum(it.action, WarmupAction.entries)
            prescriptionMetric(
                file.formatVersion,
                it.reps,
                it.durationSeconds,
                WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION
            )
        }

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

    /**
     * A prescription slot is measured by positive reps or positive whole seconds, never both and
     * never neither. A duration in a format that predates the field is rejected instead of being
     * reinterpreted as a rep count. [durationFormatVersion] is the first format that carries the
     * duration for this record family, since a timed accepted-plan slot arrived one format later
     * than a timed routine/frozen slot.
     */
    private fun prescriptionMetric(
        version: Int,
        reps: Int?,
        durationSeconds: Int?,
        durationFormatVersion: Int = PRESCRIPTION_DURATION_FORMAT_VERSION
    ) {
        if (version < durationFormatVersion && durationSeconds != null) {
            fail(BackupFailure.INVALID_VALUE)
        }
        val hasReps = reps != null && reps > 0
        val hasDuration = durationSeconds != null && durationSeconds > 0
        if (hasReps == hasDuration) fail(BackupFailure.INVALID_VALUE)
        if (reps != null && reps <= 0) fail(BackupFailure.INVALID_VALUE)
        durationSeconds?.let { if (it <= 0) fail(BackupFailure.INVALID_VALUE) }
    }

    /**
     * A recorded target must decode, must belong to the same exercise and load meaning as the set it
     * is attached to, and must not contradict the set's own measurement.
     */
    private fun checkOrderedTargets(sets: Int, encoded: String?) {
        if (encoded == null) return
        val targets = runCatching { SlotSetTargetsJson.decode(encoded) }.getOrNull()
            ?: fail(BackupFailure.INVALID_VALUE)
        // One target per prescribed set: a mismatch would address the wrong slot on restore.
        if (targets.sets != sets) fail(BackupFailure.INVALID_VALUE)
    }

    private fun prescriptionSnapshot(
        encoded: String,
        set: BackupWorkoutSetRecord,
        formatVersion: Int
    ) {
        val snapshot = try {
            WorkoutPrescriptionSnapshotJson.decode(encoded)
        } catch (_: IllegalArgumentException) {
            fail(BackupFailure.INVALID_VALUE)
        }
        // A policy on a recorded target is a format-2 target; a file that predates format 22 cannot
        // legitimately carry one.
        if (formatVersion < PROGRESSION_POLICY_FORMAT_VERSION &&
            snapshot.progressionPolicy != null
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        // A slot index or a prescribed RIR belongs to a format-3 target.
        if (formatVersion < SET_TARGETS_FORMAT_VERSION &&
            (snapshot.setIndex != null || snapshot.targetRir != null)
        ) {
            fail(BackupFailure.INVALID_VALUE)
        }
        if (snapshot.exerciseId != set.exerciseId) fail(BackupFailure.INVALID_REFERENCE)
        if (snapshot.loadKind.name != set.loadKind) fail(BackupFailure.INVALID_VALUE)
        if ((snapshot.reps == null) != (set.durationSeconds != null)) {
            fail(BackupFailure.INVALID_VALUE)
        }
        snapshot.weightKg?.let { value -> finite(value) }
    }

    private fun <E : Enum<E>> enum(value: String?, values: List<E>) {
        if (value != null && values.none { it.name == value }) fail(BackupFailure.INVALID_VALUE)
    }

    private fun fail(failure: BackupFailure): Nothing = throw BackupException(failure)

    private companion object {
        val SUPPORTED_FORMAT_VERSIONS = 1..BACKUP_FORMAT_VERSION

        const val ENTRY_KIND_ACTIVATION = "ACTIVATION"
        const val ENTRY_KIND_OCCURRENCE = "OCCURRENCE"

        /** A local time of day is minutes past midnight. */
        val MINUTE_OF_DAY_RANGE = 0..1439

        /** Muscle names accepted in an involvement snapshot, including the legacy broad names. */
        val muscleTokens: Set<String> =
            MuscleGroup.entries.map { it.name }.toSet() +
                setOf("CHEST", "BACK", "SHOULDERS", "CORE")
    }
}
