package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.equipment.LoadingInventory
import kotlinx.serialization.Serializable

/** Stable file identifiers. A reader accepts only [BACKUP_FORMAT] at a version it knows. */
const val BACKUP_FORMAT: String = "hydrafit-backup"

/** The format version this build writes. */
const val BACKUP_FORMAT_VERSION: Int = 17

const val EQUIPMENT_PROFILE_FORMAT_VERSION: Int = 14

/** The first backup format that carries per-set performed metrics (reps or whole seconds). */
const val WORKOUT_SET_METRICS_FORMAT_VERSION: Int = 17

/** The first backup format that carries local workout-import provenance. */
const val IMPORT_PROVENANCE_FORMAT_VERSION: Int = 15

/** The first backup format that carries the bucket/partition import ledger. */
const val IMPORT_BUCKET_FORMAT_VERSION: Int = 16

/** The first backup format that carries body-weight measurements and the optional weight goal. */
const val BODY_WEIGHT_FORMAT_VERSION: Int = 13

/** The first backup format that carries the rest-presentation opt-ins and workout reminders. */
const val REMINDER_FORMAT_VERSION: Int = 12

const val SETUP_FORMAT_VERSION: Int = 11

/** The first backup format that carries persistent per-session notes. */
const val SESSION_NOTES_FORMAT_VERSION: Int = 10

/** The first backup format that carries persistent per-exercise setup notes. */
const val EXERCISE_NOTES_FORMAT_VERSION: Int = 9

/**
 * A whole logical snapshot of the supported offline data. Rows are stored flat with their persisted
 * ids so restore can rebuild relationships exactly; the envelope carries the format identity, a
 * catalog fingerprint and the export time. Version 13 adds body-weight measurements and the
 * optional current goal; earlier formats restore with no recorded body weight and no goal.
 * Version 12 adds the optional
 * rest-countdown/rest-completion opt-ins and the workout-reminder preferences; earlier formats
 * restore them off, with no reminder time chosen. Version 11 adds setup disposition/resume and
 * preferred weekdays; earlier formats restore as returning users with undecided weekdays.
 * Version 10 adds persistent per-session notes;
 * earlier formats have none, so a restored legacy install reports no note rather than an explicit
 * clear. Version 9 adds persistent per-exercise setup notes; versions 1–8 have none. Version 8 adds
 * the recorded reasoning of frozen
 * activation/occurrence entries; earlier formats have none, so a restored legacy entry reports no
 * explanation rather than a fabricated one. Version 7 adds the recorded plan-target explanations;
 * earlier formats have none, so a restored legacy plan reports no explanation rather than a
 * fabricated one. Version 6 adds alert/coaching opt-ins; earlier formats default them off. Version 5
 * adds rest durations; earlier formats use the global default. Version 15 adds local workout-import
 * provenance; earlier formats have none. Version 16 adds the bucket/partition import ledger; formats
 * 1-15 restore with provenance only and cannot carry a split bucket. Version 17 adds per-set
 * performed metrics: a recorded set carries positive reps or positive whole seconds, never both.
 * Formats 1-16 have no duration field, so every restored set is repetitions and a duration in a
 * legacy file is rejected rather than reinterpreted.
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
    val sessionNotes: List<BackupSessionNoteRecord> = emptyList(),
    /** Legacy backups are returning installations, never fresh onboarding candidates. */
    val setup: BackupSetupRecord = BackupSetupRecord(),
    val preferredWeekdays: List<String>? = null,
    /**
     * Opt-in workout-reminder preferences, format 12 and later. Absent in formats 1-11, which
     * restore them off with no reminder time chosen.
     */
    val workoutReminders: BackupWorkoutReminderRecord? = null,
    /**
     * Body-weight measurements, format 13 and later: one per local civil day. Absent in formats
     * 1-12, which restore as an install with no recorded body weight.
     */
    val bodyWeightMeasurements: List<BackupBodyWeightRecord> = emptyList(),
    /** The optional current weight goal in kilograms; absent in formats 1-12 restores as no goal. */
    val bodyWeightGoalKg: Double? = null,
    /** Complete profile configuration in format 14; legacy formats carry no profile sections. */
    val equipmentProfiles: List<BackupEquipmentProfileRecord> = emptyList(),
    val activeEquipmentProfileId: Long? = null,
    /**
     * Local workout-import provenance, format 15 and later. A record is deliberately retained after
     * its imported session is deleted locally, so an entry may name a session the file does not
     * carry; that is what stops a repeated export from silently restoring removed history.
     *
     * This is the legacy single-session ledger. New imports record their whole source bucket and the
     * partition the user approved in [importBuckets]; both survive a restore.
     */
    val importProvenance: List<BackupImportProvenanceRecord> = emptyList(),
    /**
     * The bucket/partition import ledger, format 16 and later. One record per imported source bucket,
     * carrying the complete canonical bucket, the approved partition of its rows into sessions and
     * the local session each partition member created. Absent in formats 1-15.
     */
    val importBuckets: List<BackupImportBucketRecord> = emptyList()
)

/**
 * One imported source workout: the provider, a candidate key for the source workout, the canonical
 * content that produced it and the local session it created.
 */
@Serializable
data class BackupImportProvenanceRecord(
    val source: String,
    val candidateKey: String,
    val content: String,
    val importedSessionId: String
)

/**
 * One imported source bucket and its approved partition: the provider, the candidate key and
 * canonical content of the whole bucket in source order, the canonical partition manifest and the
 * local session ids the partition created, in partition order.
 */
@Serializable
data class BackupImportBucketRecord(
    val source: String,
    val candidateKey: String,
    val content: String,
    val partition: String,
    val importedSessionIds: List<String>
)

@Serializable
data class BackupEquipmentProfileRecord(
    val id: Long,
    val name: String,
    val selectedEquipment: List<String>,
    val ceilings: List<BackupEquipmentCeilingRecord>,
    val loadingInventory: LoadingInventory
)

@Serializable
data class BackupEquipmentCeilingRecord(val equipmentId: String, val maxWeightKg: Double)

@Serializable
data class BackupSetupRecord(
    val version: Int = 1,
    val disposition: String = "RETURNING",
    val step: String = "WELCOME"
)

@Serializable
data class BackupTrainingGuidanceRecord(
    val restAlertsEnabled: Boolean,
    val shortRestCoachingEnabled: Boolean,
    /** Rest-countdown presentation, added in format 12; earlier formats restore it off. */
    val restCountdownEnabled: Boolean = false,
    /** Foreground rest-completion signal, added in format 12; earlier formats restore it off. */
    val restCompletionSignalEnabled: Boolean = false
)

/**
 * Opt-in local workout reminders. [reminderMinuteOfDay] is minutes past local midnight, or null when
 * no time has been chosen yet. No OS permission or delivery state is represented here.
 */
@Serializable
data class BackupWorkoutReminderRecord(
    val remindersEnabled: Boolean,
    val reminderMinuteOfDay: Int?,
    val quietHoursEnabled: Boolean,
    val quietStartMinuteOfDay: Int,
    val quietEndMinuteOfDay: Int
)

/** One body-weight measurement keyed to a local civil day; format 13 and later. */
@Serializable
data class BackupBodyWeightRecord(val localEpochDay: Long, val weightKg: Double)

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
    /**
     * Repetitions for counted work, or null for a timed set. Formats 1-16 always carried a count,
     * which restores verbatim; a format-17 record may instead carry [durationSeconds].
     */
    val reps: Int? = null,
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
    val completedAtElapsedMillis: Long? = null,
    /** Elapsed whole seconds for a timed set, format 17 and later; null for counted work. */
    val durationSeconds: Int? = null
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

/** One session's persistent note; empty is an explicit clear, while absence means never set. */
@Serializable
data class BackupSessionNoteRecord(val sessionId: String, val note: String)
