package com.hydrafit.app.core.domain.workout

class GetWorkoutLogUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(): List<WorkoutSet> = repository.all()

    /** The open session's sets and those of the latest other session, each newest first. */
    suspend fun recentSessions(activeSessionId: String?): RecentSessionSets {
        val current = activeSessionId?.let { repository.setsBySessionNewestFirst(it) }.orEmpty()
        val lastId = repository.latestSessionIdExcluding(activeSessionId)
        val last = lastId?.let { repository.setsBySessionNewestFirst(it) }.orEmpty()
        return RecentSessionSets(current = current, last = last)
    }

    suspend fun lastWorkingSet(exerciseId: String, stationId: Long?): WorkoutSet? =
        repository.lastWorkingSetForExercise(exerciseId, stationId)

    suspend fun lastExternalLoadSet(exerciseId: String): WorkoutSet? =
        repository.lastExternalLoadSet(exerciseId)

    /** Exercises with a working set on local day [epochDay] under [utcOffsetMillis]. */
    suspend fun exerciseIdsLoggedOn(epochDay: Long, utcOffsetMillis: Long): Set<String> {
        val start = epochDay * MILLIS_PER_DAY - utcOffsetMillis
        return repository.exerciseIdsLoggedBetween(start, start + MILLIS_PER_DAY)
    }

    suspend fun setsForOccurrenceOrEntries(
        occurrenceId: Long,
        entryIds: Collection<Long>
    ): List<WorkoutSet> = repository.setsForOccurrenceOrEntries(occurrenceId, entryIds)
}

/** What Train's recent sets need: two sessions, never the whole log. */
data class RecentSessionSets(val current: List<WorkoutSet>, val last: List<WorkoutSet>)

private const val MILLIS_PER_DAY = 86_400_000L
