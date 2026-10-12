package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.Serializable

/** Stable file identifiers. A reader accepts only [BACKUP_FORMAT] at a version it knows. */
const val BACKUP_FORMAT: String = "hydrafit-backup"

/** The format version this build writes. */
const val BACKUP_FORMAT_VERSION: Int = 10

/**
 * A whole logical snapshot of the supported offline data. Rows are stored flat with their persisted
 * ids so restore can rebuild relationships exactly; the envelope carries the format identity, a
 * catalog fingerprint and the export time. Version 10 adds persistent per-session notes;
 * earlier formats have none, so a restored legacy install reports no note rather than an explicit
 * clear. Version 9 adds persistent per-exercise setup notes; versions 1–8 have none. Version 8 adds
 * the recorded reasoning of frozen
 * activation/occurrence entries; earlier formats have none, so a restored legacy entry reports no
 * explanation rather than a fabricated one. Version 7 adds the recorded plan-target explanations;
 * earlier formats have none, so a restored legacy plan reports no explanation rather than a
 * fabricated one. Version 6 adds alert/coaching opt-ins; earlier formats default them off. Version 5
 * adds rest durations; earlier formats use the global default.
 */
@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val exportedAtMillis: Long,
    val catalog: BackupCatalogManifest,
    val settings: BackupSettingsRecord?,
    val customExercises: List<BackupExerciseRecord>,
    val exerciseOverrides: List<BackupExerciseOverrideRecord>,
    val equipment: List<BackupEquipmentRecord>,
    val selectedEquipment: List<String>,
    val workoutSets: List<BackupWorkoutSetRecord>,
    val workoutSessions: List<BackupWorkoutSessionRecord>,
    val plans: List<BackupPlanRecord>,
    val planDays: List<BackupPlanDayRecord>,
    val planEntries: List<BackupPlanEntryRecord>,
    val volumeExplanations: List<BackupVolumeExplanationRecord>,
    val volumeExplanationStates: List<BackupVolumeExplanationStateRecord>,
    val routines: List<BackupRoutineRecord>,
    val routineWorkouts: List<BackupRoutineWorkoutRecord>,
    val routineEntries: List<BackupRoutineEntryRecord>,
    val activations: List<BackupActivationRecord>,
    val activationWorkouts: List<BackupActivationWorkoutRecord>,
    val activationEntries: List<BackupActivationEntryRecord>,
    val occurrences: List<BackupOccurrenceRecord>,
    val occurrenceEntries: List<BackupOccurrenceEntryRecord>,
    val scheduleState: BackupScheduleStateRecord?,
    val personalRecords: List<BackupPersonalRecordRecord>,
    val preferences: List<BackupPreferenceRecord>,
    val exclusions: List<BackupExclusionRecord>,
    val restPreferences: List<BackupRestPreferenceRecord> = emptyList(),
    val trainingGuidance: BackupTrainingGuidanceRecord = BackupTrainingGuidanceRecord(false, false),
    /**
     * Recorded plan-slot reasoning, newest format only. Absent in formats 1–6, which restores as
     * "not recorded" rather than as an empty explanation.
     */
    val planTargetExplanations: List<BackupPlanTargetExplanationRecord> = emptyList(),
    val planTargetExplanationStates: List<BackupPlanTargetExplanationStateRecord> = emptyList(),
    /**
     * Recorded reasoning of frozen activation/occurrence entries, newest format only. Absent in
     * formats 1–7, which restores as "not recorded" rather than as an empty explanation.
     */
    val entryTargetExplanations: List<BackupEntryTargetExplanationRecord> = emptyList(),
    /**
     * Persistent exercise setup notes, format 9 and later. Absent in formats 1–8, which restores as
     * "no note recorded" rather than as an explicit clear.
     */
    val exerciseNotes: List<BackupExerciseNoteRecord> = emptyList(),
    /** Notes keyed to a workout-session identity; absent before format 10. */
    val sessionNotes: List<BackupSessionNoteRecord> = emptyList()
)

@Serializable
data class BackupTrainingGuidanceRecord(
    val restAlertsEnabled: Boolean,
    val shortRestCoachingEnabled: Boolean
)

/** The seeded exercise ids the exporting install had, used to report and check catalog compatibility. */
@Serializable
data class BackupCatalogManifest(
    val seedExerciseIds: List<String>,
    val seedProfiles: List<BackupSeedProfile>
)

/**
 * A seed's stable profile string. A receiver may hold extra seeds; a profile that differs for an id
 * the receiver knows is a compatibility mismatch.
 */
@Serializable
data class BackupSeedProfile(val id: String, val profile: String)

@Serializable
data class BackupSettingsRecord(
    val engineId: String,
    val daysPerWeek: Int,
    val trainingGoal: String,
    val shareWorkoutData: Boolean,
    val weightUnit: String,
    /** Defaults off when decoding a version 1 backup that predates guided-workout opt-in. */
    val guidedWorkoutEnabled: Boolean = false
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
    val loadKind: String,
    val timingProvenance: String = "UNKNOWN",
    val startedAtElapsedMillis: Long? = null,
    val completedAtElapsedMillis: Long? = null
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

/**
 * One accepted slot's recorded reasoning. The columns mirror the stored explanation; a group is
 * present only when the app recorded it for that target.
 */
@Serializable
data class BackupPlanTargetExplanationRecord(
    val planId: Long,
    val dayIndex: Int,
    val position: Int,
    val explanationVersion: Int,
    val baselineSource: String?,
    val baselineRecentEstimateKg: Double?,
    val baselineQualifyingSetCount: Int?,
    val baselineManualFloorKg: Double?,
    val baselineKg: Double?,
    val progressionOutcome: String?,
    val progressionIncrements: Int?,
    val progressionDeltaKg: Double?,
    val progressionRoundToKg: Double?,
    val progressionSuccessStreakSize: Int?,
    val progressionFailureStreakSize: Int?,
    val conversionReps: Int?,
    val conversionIntensityFraction: Double?,
    val conversionDeloadIntensityScale: Double?,
    val conversionRoundToKg: Double?,
    val conversionUncappedWorkingKg: Double?,
    val conversionEquipmentCeilingKg: Double?,
    val conversionEquipmentCeilingBinds: Boolean?,
    val aiProposedKg: Double?,
    val aiProposedAccepted: Boolean?,
    val aiDeloadScaled: Boolean?,
    val aiDeloadIntensityScale: Double?,
    val aiProgressionBoundKg: Double?,
    val aiBoundedByProgression: Boolean?,
    val aiEquipmentCeilingKg: Double?,
    val aiCappedByEquipment: Boolean?,
    val setBaseSets: Int?,
    val setDeloadApplied: Boolean?,
    val setDeloadVolumeScale: Double?,
    val setFatigueEffect: String?,
    val setFinalSets: Int?,
    val unavailableReason: String?
)

/** Plan-level explanation context; absent for a legacy plan. */
@Serializable
data class BackupPlanTargetExplanationStateRecord(
    val planId: Long,
    val attribution: String,
    val isDeload: Boolean,
    val deloadVolumeScale: Double,
    val deloadIntensityScale: Double,
    val explanationVersion: Int
)

/**
 * One frozen entry's recorded reasoning. [entryKind] is `ACTIVATION` or `OCCURRENCE`; [entryId] is
 * the entry row it describes. The columns mirror the stored explanation; a group is present only when
 * the app recorded it for that target.
 */
@Serializable
data class BackupEntryTargetExplanationRecord(
    val entryKind: String,
    val entryId: Long,
    val explanationVersion: Int,
    val baselineSource: String?,
    val baselineRecentEstimateKg: Double?,
    val baselineQualifyingSetCount: Int?,
    val baselineManualFloorKg: Double?,
    val baselineKg: Double?,
    val progressionOutcome: String?,
    val progressionIncrements: Int?,
    val progressionDeltaKg: Double?,
    val progressionRoundToKg: Double?,
    val progressionSuccessStreakSize: Int?,
    val progressionFailureStreakSize: Int?,
    val conversionReps: Int?,
    val conversionIntensityFraction: Double?,
    val conversionDeloadIntensityScale: Double?,
    val conversionRoundToKg: Double?,
    val conversionUncappedWorkingKg: Double?,
    val conversionEquipmentCeilingKg: Double?,
    val conversionEquipmentCeilingBinds: Boolean?,
    val aiProposedKg: Double?,
    val aiProposedAccepted: Boolean?,
    val aiDeloadScaled: Boolean?,
    val aiDeloadIntensityScale: Double?,
    val aiProgressionBoundKg: Double?,
    val aiBoundedByProgression: Boolean?,
    val aiEquipmentCeilingKg: Double?,
    val aiCappedByEquipment: Boolean?,
    val setBaseSets: Int?,
    val setDeloadApplied: Boolean?,
    val setDeloadVolumeScale: Double?,
    val setFatigueEffect: String?,
    val setFinalSets: Int?,
    val unavailableReason: String?
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

@Serializable
data class BackupRestPreferenceRecord(val exerciseId: String?, val durationSeconds: Long)

/** One exercise's persistent setup note. The [note] may be empty, an explicit clear. */
@Serializable
data class BackupExerciseNoteRecord(val exerciseId: String, val note: String)

@Serializable
data class BackupSessionNoteRecord(val sessionId: String, val note: String)
