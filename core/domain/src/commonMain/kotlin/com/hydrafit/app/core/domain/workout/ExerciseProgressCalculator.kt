package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.OneRepMax
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.time.localEpochDay

/**
 * Incremental replay of one exercise, oldest first. Persistence can feed bounded pages without
 * retaining the full log. Only output records, requested occurrence events and chart days survive.
 * Rows sharing a timestamp compare with the same earlier baseline, never an invented id chronology.
 */
class ExerciseProgressCalculator(private val request: ExerciseProgressRequest) {
    private val estimatePolicy = SuggestedWeightConfig()
    private val records = linkedMapOf<ProgressRecordKey, ProgressRecord>()
    private val days = linkedMapOf<Long, DayAccumulator>()
    private val events = mutableListOf<ProgressRecordEvent>()
    private var timestamp: Long? = null
    private val earlier = mutableMapOf<ProgressRecordKey, ProgressRecord?>()

    fun accept(set: WorkoutSet) {
        require(set.exerciseId == request.exerciseId)
        require(timestamp == null || set.performedAtMillis >= timestamp!!)
        if (timestamp != set.performedAtMillis) {
            earlier.clear()
            timestamp = set.performedAtMillis
        }
        if (set.isWarmup || set.performedAtMillis > request.nowMillis) return
        val day = localEpochDay(set.performedAtMillis, request.utcOffsetMillis)
        val chart = if (day in request.dayFrom..request.dayTo) {
            days.getOrPut(day) { DayAccumulator(day) }.also { it.add(set) }
        } else {
            null
        }
        candidates(set).forEach { candidate ->
            if (!earlier.containsKey(candidate.key)) earlier[candidate.key] = records[candidate.key]
            val previous = earlier[candidate.key]
            if (previous != null &&
                candidate.value > previous.value &&
                request.occurrenceId != null &&
                set.occurrenceId == request.occurrenceId
            ) {
                events += ProgressRecordEvent(set.id, set.occurrenceId, candidate)
            }
            merge(records, candidate)
            if (candidate.key.type == ProgressRecordType.ESTIMATED_ONE_REP_MAX) {
                chart?.let { merge(it.estimates, candidate) }
            }
        }
    }

    fun result(): ExerciseProgress = ExerciseProgress(
        days.values.sortedBy { it.day }.map { it.result() },
        records.values.toList(),
        events.toList()
    )

    private fun candidates(set: WorkoutSet): List<ProgressRecord> {
        if (set.loadKind == LoadKind.LEGACY_UNSPECIFIED) return emptyList()
        val load = set.weightKg
        val result = mutableListOf<ProgressRecord>()
        if (load != null) {
            result += record(ProgressRecordType.LOAD_AT_REPS, set, load, reps = set.reps)
        }
        result += record(ProgressRecordType.REPS_AT_LOAD, set, set.reps.toDouble(), weightKg = load)
        if (set.loadKind == LoadKind.EXTERNAL &&
            load != null &&
            load > 0.0 &&
            estimatePolicy.hasUsableEstimate(set.reps)
        ) {
            result += record(
                ProgressRecordType.ESTIMATED_ONE_REP_MAX,
                set,
                OneRepMax.estimate(load, set.reps)
            )
        }
        return result
    }

    private class DayAccumulator(val day: Long) {
        var count = 0
        var reps = 0L
        var volume: Double? = null
        val loads = linkedMapOf<ProgressRecordKey, ProgressRecord>()
        val repetitions = linkedMapOf<ProgressRecordKey, ProgressRecord>()
        val estimates = linkedMapOf<ProgressRecordKey, ProgressRecord>()

        fun add(set: WorkoutSet) {
            count++
            reps += set.reps.toLong()
            if (set.loadKind == LoadKind.EXTERNAL && set.weightKg != null) {
                volume = (volume ?: 0.0) + set.weightKg * set.reps
            }
            set.weightKg?.let {
                merge(loads, record(ProgressRecordType.LOAD_AT_REPS, set, it))
            }
            merge(repetitions, record(ProgressRecordType.REPS_AT_LOAD, set, set.reps.toDouble()))
        }

        fun result() = ProgressDay(
            day,
            count,
            reps,
            volume,
            loads.values.toList(),
            repetitions.values.toList(),
            estimates.values.singleOrNull()
        )
    }

    companion object {
        private fun record(
            type: ProgressRecordType,
            set: WorkoutSet,
            value: Double,
            reps: Int? = null,
            weightKg: Double? = null
        ) = ProgressRecord(
            ProgressRecordKey(type, set.loadKind, reps, weightKg),
            value,
            listOf(set.id)
        )

        private fun merge(
            target: MutableMap<ProgressRecordKey, ProgressRecord>,
            value: ProgressRecord
        ) {
            val previous = target[value.key]
            when {
                previous == null || value.value > previous.value -> target[value.key] = value
                value.value == previous.value -> target[value.key] = previous.copy(
                    setIds = previous.setIds + value.setIds
                )
            }
        }
    }
}
