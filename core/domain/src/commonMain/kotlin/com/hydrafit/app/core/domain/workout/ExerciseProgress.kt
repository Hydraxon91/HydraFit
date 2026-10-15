package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/** Inclusive civil days, interpreted with one supplied offset, not historical zone rules. */
data class ExerciseProgressRequest(
    val exerciseId: String,
    val dayFrom: Long,
    val dayTo: Long,
    val nowMillis: Long,
    val utcOffsetMillis: Long = 0L,
    val occurrenceId: Long? = null
) {
    init {
        require(exerciseId.isNotBlank())
        require(dayFrom <= dayTo)
        require(utcOffsetMillis in -86_400_000L..86_400_000L)
    }
}

/** A cursor belongs to exactly one exercise; timestamps and ids are stored values. */
data class ExerciseHistoryCursor(
    val exerciseId: String,
    val performedAtMillis: Long,
    val setId: Long
)

data class ExerciseHistoryRequest(
    val exerciseId: String,
    val pageSize: Int = 30,
    val cursor: ExerciseHistoryCursor? = null
) {
    init {
        require(exerciseId.isNotBlank())
        require(pageSize in 1..100)
        require(cursor == null || cursor.exerciseId == exerciseId)
    }
}

data class ExerciseHistoryPage(val sets: List<WorkoutHistorySet>, val hasMore: Boolean) {
    val nextCursor: ExerciseHistoryCursor?
        get() = if (hasMore) {
            sets.lastOrNull()?.let {
                ExerciseHistoryCursor(it.exerciseId, it.performedAtMillis, it.id)
            }
        } else {
            null
        }
}

enum class ProgressRecordType { LOAD_AT_REPS, REPS_AT_LOAD, ESTIMATED_ONE_REP_MAX }

/** Load kinds are separate comparison populations; absent load is not explicit zero. */
data class ProgressRecordKey(
    val type: ProgressRecordType,
    val loadKind: LoadKind,
    val reps: Int? = null,
    val weightKg: Double? = null
)

data class ProgressRecord(val key: ProgressRecordKey, val value: Double, val setIds: List<Long>)

/** Only strict improvements over an earlier timestamp; first observations are baselines. */
data class ProgressRecordEvent(val setId: Long, val occurrenceId: Long?, val record: ProgressRecord)

data class ProgressDay(
    val localEpochDay: Long,
    val workingSets: Int,
    val totalReps: Long,
    val externalVolumeKg: Double?,
    val bestLoads: List<ProgressRecord>,
    val bestReps: List<ProgressRecord>,
    val estimatedOneRepMax: ProgressRecord?
)

data class ExerciseProgress(
    val days: List<ProgressDay>,
    val records: List<ProgressRecord>,
    /** Events for the requested occurrence only; empty when no occurrence was requested. */
    val occurrenceEvents: List<ProgressRecordEvent>
)

interface ExerciseProgressRepository {
    fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage>

    /** Complete chart range and all-time records through [ExerciseProgressRequest.nowMillis]. */
    fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress>
}
