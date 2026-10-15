package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.localEpochDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * One chronological record improvement achieved while logging [occurrenceId], with the contributing
 * set's current recorded details when it still resolves, or null once it no longer does.
 *
 * The achievement is descriptive: it says a recorded set improved on this exercise's chronological
 * history, not that a prescription was fulfilled or technique verified. [event] keeps the full
 * record key and the stable exercise identity so a consumer can label the metric and its load kind.
 */
data class OccurrenceRecordImprovement(
    val exerciseId: String,
    val event: ProgressRecordEvent,
    val set: WorkoutHistorySet?
)

/**
 * Observes the record improvements achieved by one workout occurrence's recorded work.
 *
 * Reactively combines the occurrence's distinct recorded exercises with each one's chronological
 * record events, so a correction, deletion, restore or replacement recomputes the improvements and a
 * removed exercise retracts. Events are keyed by the stable exercise id and returned in the
 * occurrence's stable exercise order, each exercise's events in chronological order.
 *
 * The chart range is deliberately the single civil day of [nowMillis]: record events and their
 * all-time baseline never depend on the range, so the range only bounds the unused chart projection.
 * Each per-exercise replay excludes future sets through the same [nowMillis]. Empty occurrence emits
 * an empty list; a read failure propagates so the caller can offer a retry; cancellation cancels the
 * in-flight readers. Read-only: it never writes a personal record, a set or the planner baseline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOccurrenceRecordsUseCase(private val repository: ExerciseProgressRepository) {
    operator fun invoke(
        occurrenceId: Long,
        nowMillis: Long,
        utcOffsetMillis: Long
    ): Flow<List<OccurrenceRecordImprovement>> {
        val today = localEpochDay(nowMillis, utcOffsetMillis)
        return repository.observeOccurrenceExerciseIds(occurrenceId).flatMapLatest { exerciseIds ->
            if (exerciseIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                val perExercise = exerciseIds.map { exerciseId ->
                    repository.observeProgress(
                        ExerciseProgressRequest(
                            exerciseId = exerciseId,
                            dayFrom = today,
                            dayTo = today,
                            nowMillis = nowMillis,
                            utcOffsetMillis = utcOffsetMillis,
                            occurrenceId = occurrenceId
                        )
                    )
                }
                combine(perExercise) { progresses ->
                    exerciseIds.flatMapIndexed { index, exerciseId ->
                        progresses[index].occurrenceEvents.map { event ->
                            OccurrenceRecordImprovement(exerciseId, event, null)
                        }
                    }
                }
            }
        }.flatMapLatest { improvements -> resolveSets(improvements) }
    }

    /**
     * Resolves each improvement's contributing set reactively, so a correction or rename refreshes
     * the shown details and a deleted set resolves to null without dropping the event line.
     */
    private fun resolveSets(
        improvements: List<OccurrenceRecordImprovement>
    ): Flow<List<OccurrenceRecordImprovement>> {
        if (improvements.isEmpty()) return flowOf(improvements)
        val perSet = improvements.map { improvement ->
            repository.observeSet(improvement.exerciseId, improvement.event.setId)
                .map { set -> improvement.copy(set = set) }
        }
        return combine(perSet) { it.toList() }
    }
}
