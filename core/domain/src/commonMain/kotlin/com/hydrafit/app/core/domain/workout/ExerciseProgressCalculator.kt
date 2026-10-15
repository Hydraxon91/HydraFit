package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.OneRepMax
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.time.localEpochDay

/**
 * Incremental replay of one exercise, oldest first. Persistence can feed bounded pages without
 * retaining the full log. Only output records, requested occurrence events and chart days survive.
 * Rows sharing a timestamp compare with the same earlier baseline, never an invented id chronology.
 * Tied holders accumulate in place and each [result] copies them out, so a snapshot a caller already
 * holds never changes when more input arrives.
 */
class ExerciseProgressCalculator(private val request: ExerciseProgressRequest) {
    private val estimatePolicy = SuggestedWeightConfig()
    private val records = linkedMapOf<ProgressRecordKey, Accumulator>()
    private val days = linkedMapOf<Long, DayAccumulator>()
    private val events = mutableListOf<ProgressRecordEvent>()
    private var timestamp: Long? = null

    /** Value each key already held before the current timestamp, captured on first sight. */
    private val earlier = mutableMapOf<ProgressRecordKey, Double?>()

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
            if (!earlier.containsKey(candidate.key)) {
                earlier[candidate.key] = records[candidate.key]?.value
            }
            val baseline = earlier[candidate.key]
            if (baseline != null &&
                candidate.value > baseline &&
                request.occurrenceId != null &&
                set.occurrenceId == request.occurrenceId
            ) {
                events += ProgressRecordEvent(set.id, set.occurrenceId, candidate)
            }
            records.merge(candidate)
            if (candidate.key.type == ProgressRecordType.ESTIMATED_ONE_REP_MAX) {
                chart?.let { it.estimates.merge(candidate) }
            }
        }
    }

    fun result(): ExerciseProgress = ExerciseProgress(
        days.values.sortedBy { it.day }.map { it.result() },
        records.values.map { it.snapshot() },
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
        val loads = linkedMapOf<ProgressRecordKey, Accumulator>()
        val repetitions = linkedMapOf<ProgressRecordKey, Accumulator>()
        val estimates = linkedMapOf<ProgressRecordKey, Accumulator>()

        fun add(set: WorkoutSet) {
            count++
            reps += set.reps.toLong()
            if (set.loadKind == LoadKind.EXTERNAL && set.weightKg != null) {
                volume = (volume ?: 0.0) + set.weightKg * set.reps
            }
            set.weightKg?.let {
                loads.merge(record(ProgressRecordType.LOAD_AT_REPS, set, it))
            }
            repetitions.merge(record(ProgressRecordType.REPS_AT_LOAD, set, set.reps.toDouble()))
        }

        fun result() = ProgressDay(
            day,
            count,
            reps,
            volume,
            loads.values.map { it.snapshot() },
            repetitions.values.map { it.snapshot() },
            estimates.values.singleOrNull()?.snapshot()
        )
    }

    private companion object {
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
    }
}

/**
 * Mutable holder for one record key. A strict improvement replaces both value and holders; a tie
 * appends the extra set id. Appending is amortized O(1), avoiding the per-row list copy that made a
 * long tie population quadratic. [snapshot] copies the ids so a returned record never observes later
 * input.
 */
private class Accumulator(val key: ProgressRecordKey, value: Double) {
    var value: Double = value
    val setIds = mutableListOf<Long>()

    fun accept(incoming: ProgressRecord) {
        if (incoming.value > value) {
            value = incoming.value
            setIds.clear()
            setIds += incoming.setIds
        } else if (incoming.value == value) {
            setIds += incoming.setIds
        }
    }

    fun snapshot() = ProgressRecord(key, value, setIds.toList())
}

private fun MutableMap<ProgressRecordKey, Accumulator>.merge(value: ProgressRecord) {
    val existing = this[value.key]
    if (existing == null) {
        this[value.key] = Accumulator(value.key, value.value).also { it.setIds += value.setIds }
    } else {
        existing.accept(value)
    }
}
