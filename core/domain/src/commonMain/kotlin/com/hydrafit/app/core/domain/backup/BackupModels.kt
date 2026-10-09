package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.Serializable

/** Stable file identifiers. A reader accepts only [BACKUP_FORMAT] at a version it knows. */
const val BACKUP_FORMAT: String = "hydrafit-backup"

/** The only format version this build writes and reads. */
const val BACKUP_FORMAT_VERSION: Int = 1

/**
 * A whole logical snapshot of the supported offline data. Rows are stored flat with their persisted
 * ids so restore can rebuild relationships exactly; the envelope carries the format identity, a
 * catalog fingerprint and the export time. Nullable fields are explicit `null` rather than omitted.
 */
@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appVersion: String,
    val exportedAtMillis: Long,
    val catalog: BackupCatalogManifest,
    val settings: BackupSettingsRecord? = null,
    val customExercises: List<BackupExerciseRecord> = emptyList(),
    val exerciseOverrides: List<BackupExerciseOverrideRecord> = emptyList(),
    val equipment: List<BackupEquipmentRecord> = emptyList(),
    val selectedEquipment: List<String> = emptyList(),
    val workoutSets: List<BackupWorkoutSetRecord> = emptyList(),
    val workoutSessions: List<BackupWorkoutSessionRecord> = emptyList(),
    val plans: List<BackupPlanRecord> = emptyList(),
    val planDays: List<BackupPlanDayRecord> = emptyList(),
    val planEntries: List<BackupPlanEntryRecord> = emptyList(),
    val volumeExplanations: List<BackupVolumeExplanationRecord> = emptyList(),
    val volumeExplanationStates: List<BackupVolumeExplanationStateRecord> = emptyList(),
    val routines: List<BackupRoutineRecord> = emptyList(),
    val routineWorkouts: List<BackupRoutineWorkoutRecord> = emptyList(),
    val routineEntries: List<BackupRoutineEntryRecord> = emptyList(),
    val activations: List<BackupActivationRecord> = emptyList(),
    val activationWorkouts: List<BackupActivationWorkoutRecord> = emptyList(),
    val activationEntries: List<BackupActivationEntryRecord> = emptyList(),
    val occurrences: List<BackupOccurrenceRecord> = emptyList(),
    val occurrenceEntries: List<BackupOccurrenceEntryRecord> = emptyList(),
    val scheduleState: BackupScheduleStateRecord? = null,
    val personalRecords: List<BackupPersonalRecordRecord> = emptyList(),
    val preferences: List<BackupPreferenceRecord> = emptyList(),
    val exclusions: List<BackupExclusionRecord> = emptyList()
)

/** The seeded exercise ids the exporting install had; used to report and check catalog compatibility. */
@Serializable
data class BackupCatalogManifest(val seedExerciseIds: List<String>)

@Serializable
data class BackupSettingsRecord(
    val engineId: String,
    val daysPerWeek: Int,
    val trainingGoal: String,
    val shareWorkoutData: Boolean,
    val weightUnit: String
)

@Serializable
data class BackupExerciseRecord(
    val id: String,
    val name: String,
    val requiredEquipment: String,
    val movementPattern: String,
    val isUnilateral: Boolean,
    val loadCapability: String,
    val involvements: String?
)

@Serializable
data class BackupExerciseOverrideRecord(
    val exerciseId: String,
    val name: String?,
    val requiredEquipment: String?,
    val movementPattern: String?,
    val isUnilateral: Boolean?,
    val loadCapability: String?,
    val involvements: String?
)

@Serializable
data class BackupEquipmentRecord(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val maxWeightKg: Double?
)

@Serializable
data class BackupWorkoutSetRecord(
    val id: Long,
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double?,
    val performedAtMillis: Long,
    val isWarmup: Boolean,
    val involvements: String?,
    val weekNumber: Int?,
    val cycleNumber: Int?,
    val dayIndex: Int?,
    val rir: Int?,
    val sessionId: String?,
    val occurrenceId: Long?,
    val occurrenceEntryId: Long?,
    val loadKind: String
)

@Serializable
data class BackupWorkoutSessionRecord(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val localEpochDay: Long,
    val occurrenceId: Long?
)

@Serializable
data class BackupPlanRecord(
    val id: Long,
    val engineId: String,
    val acceptedAt: Long,
    val weekNumber: Int,
    val cycleNumber: Int
)

@Serializable
data class BackupPlanDayRecord(val id: Long, val planId: Long, val dayIndex: Int, val focus: String)

@Serializable
data class BackupPlanEntryRecord(
    val id: Long,
    val dayId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val exerciseName: String,
    val movementPattern: String,
    val suggestedWeightKg: Double?,
    val loadCapability: String,
    val loadKind: String
)

@Serializable
data class BackupVolumeExplanationRecord(
    val planId: Long,
    val muscle: String,
    val targetSets: Int,
    val isTargetEnforced: Boolean,
    val directIsolationSets: Int,
    val estimatedOtherInvolvementCredits: Double,
    val unmetReason: String?,
    val attribution: String
)

@Serializable
data class BackupVolumeExplanationStateRecord(
    val planId: Long,
    val status: String,
    val assessmentVersion: Int
)

@Serializable
data class BackupRoutineRecord(
    val id: Long,
    val name: String,
    val revision: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val archivedAtMillis: Long?,
    val sourcePlanId: Long?
)

@Serializable
data class BackupRoutineWorkoutRecord(
    val id: Long,
    val templateId: Long,
    val position: Int,
    val name: String,
    val focus: String?
)

@Serializable
data class BackupRoutineEntryRecord(
    val id: Long,
    val workoutId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val loadKind: String
)

@Serializable
data class BackupActivationRecord(
    val id: Long,
    val templateId: Long?,
    val templateRevision: Int?,
    val sourcePlanId: Long?,
    val name: String,
    val createdAtMillis: Long,
    val startEpochDay: Long,
    val mode: String,
    val weekdayMask: Long,
    val status: String,
    val weekNumber: Int?,
    val cycleNumber: Int?,
    val endedAtMillis: Long?,
    val revision: Int
)

@Serializable
data class BackupActivationWorkoutRecord(
    val id: Long,
    val activationId: Long,
    val sourceWorkoutId: Long?,
    val position: Int,
    val name: String,
    val focus: String?
)

@Serializable
data class BackupActivationEntryRecord(
    val id: Long,
    val workoutId: Long,
    val position: Int,
    val exerciseId: String,
    val exerciseName: String,
    val movementPattern: String,
    val requiredEquipment: String,
    val involvements: String?,
    val isUnilateral: Boolean,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val loadCapability: String,
    val loadKind: String
)

@Serializable
data class BackupOccurrenceRecord(
    val id: Long,
    val activationId: Long,
    val activationWorkoutId: Long,
    val queuePosition: Int,
    val scheduledEpochDay: Long?,
    val notBeforeEpochDay: Long?,
    val startedAtMillis: Long?,
    val resolvedAtMillis: Long?,
    val status: String,
    val revision: Int
)

@Serializable
data class BackupOccurrenceEntryRecord(
    val id: Long,
    val occurrenceId: Long,
    val sourceActivationEntryId: Long?,
    val position: Int,
    val exerciseId: String,
    val exerciseName: String,
    val movementPattern: String,
    val requiredEquipment: String,
    val involvements: String?,
    val isUnilateral: Boolean,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val loadCapability: String,
    val loadKind: String,
    val remainingDisposition: String?,
    val terminalRemainingSets: Int?
)

@Serializable
data class BackupScheduleStateRecord(
    val activeActivationId: Long?,
    val selectedOccurrenceId: Long?,
    val legacyFallbackEnabled: Boolean
)

@Serializable
data class BackupPersonalRecordRecord(
    val exerciseId: String,
    val weightKg: Double,
    val reps: Int,
    val updatedAt: Long,
    val loadKind: String
)

@Serializable
data class BackupPreferenceRecord(val exerciseId: String, val preference: String)

@Serializable
data class BackupExclusionRecord(val exerciseId: String, val expiresAt: Long?)
