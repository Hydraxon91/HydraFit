package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.equipment.LoadingInventory
import kotlinx.serialization.Serializable

/** Stable file identifiers. A reader accepts only [BACKUP_FORMAT] at a version it knows. */
const val BACKUP_FORMAT: String = "hydrafit-backup"

/** The format version this build writes. */
const val BACKUP_FORMAT_VERSION: Int = 29

/**
 * Format 27 adds measured force in canonical newtons to performed sets and every prescription
 * store, plus a performed set's descriptive device identity and stated rating. A format-26 or older
 * file must not carry any of them: a rating is never converted into force or lifted mass.
 */
const val FORCE_FORMAT_VERSION: Int = 27

/** The first format that may carry a performed or prescribed cluster. */
const val CLUSTER_FORMAT_VERSION: Int = 28

/**
 * Format 29 adds manually recorded cardio activities. They are whole-replaced on restore and added
 * add-only by identity on merge; a pre-29 file must not carry any, because the record has no earlier
 * representation and dropping it would silently lose recorded activity.
 */
const val CARDIO_FORMAT_VERSION: Int = 29

/** Format 26 adds whole-metre set/prescription distance and the metres/feet preference. */
const val DISTANCE_FORMAT_VERSION: Int = 26

/** Format 24 carries explicit two-entry superset membership and user-configured rest. */
const val SUPERSET_FORMAT_VERSION: Int = 24

/**
 * Format 25 adds profile-scoped equipment stations and a performed set's frozen station
 * attribution. Earlier formats restore with no stations and no attributed sets.
 */
const val EQUIPMENT_STATION_FORMAT_VERSION: Int = 25

/**
 * Format 22 adds a prescription slot's approved progression policy to routine, accepted-plan,
 * activation and occurrence entries, and permits a format-2 recorded target that carries it. A
 * pre-22 file must not carry either, so an older reader never receives a rule it cannot evaluate.
 */
const val PROGRESSION_POLICY_FORMAT_VERSION: Int = 22

/**
 * Format 23 adds a prescription slot's ordered per-set targets and permits a format-3 recorded target
 * that carries a slot index and a prescribed RIR. A pre-23 file must not carry either, so no
 * historical set is given an invented slot identity by a reader that predates ordered prescriptions.
 */
const val SET_TARGETS_FORMAT_VERSION: Int = 23

/** The first backup format that carries a performed set's recorded prescription target. */
const val PRESCRIPTION_SNAPSHOT_FORMAT_VERSION: Int = 21

/** The first backup format that carries a timed prescription slot's duration. */
const val PRESCRIPTION_DURATION_FORMAT_VERSION: Int = 18

/** The first backup format that carries an accepted plan slot's duration. */
const val PLAN_ENTRY_DURATION_FORMAT_VERSION: Int = 19

/** The first backup format that carries persisted warm-up preparation overrides. */
const val WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION: Int = 20

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
 * legacy file is rejected rather than reinterpreted. Version 18 adds a timed prescription slot's
 * duration for routines and frozen work; formats 1-17 restore every prescription slot as
 * rep-measured. Version 19 adds an accepted plan slot's duration; formats 1-18 restore every
 * accepted-plan slot as rep-measured, and a duration in an older file is rejected rather than
 * reinterpreted. Version 20 adds the persisted warm-up preparation overrides; formats 1-19 restore
 * with none, so every preparation action uses its built-in default. Version 25 adds profile-scoped
 * equipment stations and a performed set's frozen station attribution; formats 1-24 restore with no
 * stations and unattributed sets, and merge keeps the frozen label while dropping the local id.
 * Version 26 adds whole-metre distance and the metres/feet preference. Version 27 adds measured force
 * and a performed set's descriptive device identity; formats 1-26 restore with no force and no device,
 * and a force or device value in an older file is rejected rather than reinterpreted. Version 29 adds
 * manually recorded cardio activities; formats 1-28 carry none, and a cardio activity in an older
 * file is rejected rather than dropped.
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
    /** Additional performed cluster segments, format 28 and later; position 0 is the parent set. */
    val workoutClusterSegments: List<BackupClusterSegmentRecord> = emptyList(),
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
    val importBuckets: List<BackupImportBucketRecord> = emptyList(),
    /**
     * Persisted warm-up preparation overrides, format 20 and later: one record per preparation
     * action. Absent in formats 1-19, which restore every preparation action to its built-in default.
     */
    val warmupPreparationOverrides: List<BackupWarmupPreparationOverrideRecord> = emptyList(),
    /** Profile-scoped physical stations, format 25 and later. */
    val equipmentStations: List<BackupEquipmentStationRecord> = emptyList(),
    /**
     * Manually recorded cardio activities, format 29 and later, oldest first by start time with ties
     * broken by identity so repeated exports are byte-stable. A cardio activity is not a resistance
     * set: it never links to a session, occurrence or performed set.
     */
    val cardioActivities: List<BackupCardioActivityRecord> = emptyList()
)

/**
 * One manually recorded cardio activity. [identity] is the stable, device-generated key that an
 * add-only merge recognises; no local row id is carried, because a merge assigns a fresh local id.
 * The bounds (identity length, start time, duration, distance and note) live in
 * [com.hydrafit.app.core.domain.cardio.CardioActivity], which validation constructs.
 */
@Serializable
data class BackupCardioActivityRecord(
    val identity: String,
    val type: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val distanceMeters: Int? = null,
    val note: String? = null
)

/**
 * One preparation action's persisted override. [reps] and [durationSeconds] are exclusive — exactly
 * one is positive — so a timed action is never restored as reps or vice versa.
 */
@Serializable
data class BackupWarmupPreparationOverrideRecord(
    val action: String,
    val reps: Int? = null,
    val durationSeconds: Int? = null
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
    val guidedWorkoutEnabled: Boolean = false,
    /** Metres/feet preference, format 26 and later; earlier formats restore metres. */
    val distanceUnit: String = "METERS"
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

/** One profile-scoped station, including its optional complete loading grid. */
@Serializable
data class BackupEquipmentStationRecord(
    val id: Long,
    val profileId: Long,
    val equipmentId: String,
    val name: String,
    val loadBasis: String,
    val minWeightKg: Double? = null,
    val maxWeightKg: Double? = null,
    val incrementKg: Double? = null,
    val retired: Boolean = false
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
    val durationSeconds: Int? = null,
    /**
     * The recorded prescription target this set was performed against, format 21 and later, encoded
     * by [WorkoutPrescriptionSnapshotJson]. Formats 1-20 have no target, so a restored legacy set
     * reports "unknown target" rather than inheriting today's accepted plan.
     */
    val prescriptionSnapshot: String? = null,
    /** Local station identity, format 25 and later; null for legacy and unattributed work. */
    val stationId: Long? = null,
    /** Frozen station name, format 25 and later. */
    val stationName: String? = null,
    /** Frozen station load basis, format 25 and later. */
    val stationLoadBasis: String? = null,
    /** Whole metres for a distance set, format 26 and later. */
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, format 27 and later; null for every other metric. */
    val forceNewtons: Double? = null,
    /** The measuring device's frozen name, format 27 and later; descriptive identity only. */
    val deviceName: String? = null,
    /**
     * The measuring device's stated resistance rating in kilograms, format 27 and later. It is never
     * converted into force or lifted mass; comparisons require the same device identity.
     */
    val deviceRatingKg: Double? = null,
    /** Cluster kind, format 28 and later; null for an ordinary set. */
    val clusterKind: String? = null,
    /** Prescribed segment count, format 28 and later; present only with a cluster kind. */
    val clusterPlannedSegments: Int? = null,
    /** Performed segment count, format 28 and later; a partial cluster satisfies no position. */
    val clusterPerformedSegments: Int? = null
)

@Serializable
data class BackupClusterSegmentRecord(
    /** The performed cluster parent this segment belongs to; always a cluster parent in the file. */
    val setId: Long,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
    val rir: Int? = null
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
    /**
     * Repetitions for counted work, or null for a timed slot. Formats 1-18 always carried a count,
     * which restores verbatim; a format-19 record may instead carry [durationSeconds].
     */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed slot, format 19 and later; null for counted work. */
    val durationSeconds: Int? = null,
    val exerciseName: String,
    val movementPattern: String,
    val suggestedWeightKg: Double?,
    val loadCapability: String,
    val loadKind: String,
    /**
     * The encoded slot progression policy, format 22 and later. Absent in earlier formats, which
     * restore the slot with no policy configured rather than with an inherited rule.
     */
    val progressionPolicy: String? = null,
    /** The encoded ordered per-set targets, format 23 and later; null when the sets repeat. */
    val setTargets: String? = null,
    val superset: String? = null,
    /** The encoded ordered cluster prescription, format 28 and later; null for ordinary work. */
    val cluster: String? = null,
    /** Whole metres for a distance slot, format 26 and later. */
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, format 27 and later. */
    val forceNewtons: Double? = null
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
    /** Repetitions for counted work, or null for a timed slot; formats before 18 always counted. */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed slot, format 18 and later; null for counted work. */
    val durationSeconds: Int? = null,
    val weightKg: Double?,
    val loadKind: String,
    /**
     * The encoded slot progression policy, format 22 and later. Absent in earlier formats, which
     * restore the slot with no policy configured rather than with an inherited rule.
     */
    val progressionPolicy: String? = null,
    /** The encoded ordered per-set targets, format 23 and later; null when the sets repeat. */
    val setTargets: String? = null,
    val superset: String? = null,
    /** The encoded ordered cluster prescription, format 28 and later; null for ordinary work. */
    val cluster: String? = null,
    /** Whole metres for a distance slot, format 26 and later. */
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, format 27 and later. */
    val forceNewtons: Double? = null
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
    /** Repetitions for counted work, or null for a timed slot; formats before 18 always counted. */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed slot, format 18 and later; null for counted work. */
    val durationSeconds: Int? = null,
    val weightKg: Double?,
    val loadCapability: String,
    val loadKind: String,
    /** The encoded slot progression policy, format 22 and later; null when none is configured. */
    val progressionPolicy: String? = null,
    /** The encoded ordered per-set targets, format 23 and later; null when the sets repeat. */
    val setTargets: String? = null,
    val superset: String? = null,
    /** The encoded ordered cluster prescription, format 28 and later; null for ordinary work. */
    val cluster: String? = null,
    /** Whole metres for a distance slot, format 26 and later. */
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, format 27 and later. */
    val forceNewtons: Double? = null
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
    /** Repetitions for counted work, or null for a timed slot; formats before 18 always counted. */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed slot, format 18 and later; null for counted work. */
    val durationSeconds: Int? = null,
    val weightKg: Double?,
    val loadCapability: String,
    val loadKind: String,
    val remainingDisposition: String?,
    val terminalRemainingSets: Int?,
    /** The encoded slot progression policy, format 22 and later; null when none is configured. */
    val progressionPolicy: String? = null,
    /** The encoded ordered per-set targets, format 23 and later; null when the sets repeat. */
    val setTargets: String? = null,
    val superset: String? = null,
    /** The encoded ordered cluster prescription, format 28 and later; null for ordinary work. */
    val cluster: String? = null,
    /** Whole metres for a distance slot, format 26 and later. */
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, format 27 and later. */
    val forceNewtons: Double? = null
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
