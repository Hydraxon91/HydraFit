package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Sessions per Monday-to-Sunday week for the last [WeeklyTrainingCounts.WEEKS] weeks, oldest first,
 * so the last entry is the current week. There is deliberately no streak or "behind" state: a week
 * with no session is just a zero, and nothing here resets or penalises a rest or recovery week.
 */
data class WeeklyTrainingSummary(
    val sessionsPerWeek: List<Int>,
    /** The days per week of the latest accepted plan, or null when none has been accepted. */
    val plannedDaysPerWeek: Int?,
    /** True while the latest accepted plan is a deload week, so a lighter week reads as planned. */
    val isDeloadWeek: Boolean = false
) {
    val sessionsThisWeek: Int get() = sessionsPerWeek.lastOrNull() ?: 0
}

object WeeklyTrainingCounts {
    const val WEEKS = 4
    private const val DAYS_PER_WEEK = 7L

    /** 1970-01-01 was a Thursday, so day 0 is three days after a Monday. */
    private const val EPOCH_DAYS_SINCE_MONDAY = 3L

    /** The local epoch day of the Monday that starts the week containing [localEpochDay]. */
    fun mondayOf(localEpochDay: Long): Long =
        localEpochDay - (localEpochDay + EPOCH_DAYS_SINCE_MONDAY).mod(DAYS_PER_WEEK)

    /** The first day a summary of [weeks] weeks ending at [todayEpochDay] needs to look at. */
    fun firstDay(todayEpochDay: Long, weeks: Int = WEEKS): Long =
        mondayOf(todayEpochDay) - (weeks - 1) * DAYS_PER_WEEK

    /**
     * Counts [sessions] per week for the [weeks] weeks ending with the week of [todayEpochDay].
     * Sessions on a day after today are ignored, and each session counts once.
     */
    fun sessionsPerWeek(
        sessions: Collection<WorkoutSession>,
        todayEpochDay: Long,
        weeks: Int = WEEKS
    ): List<Int> {
        require(weeks >= 1) { "weeks must be at least 1" }
        val first = firstDay(todayEpochDay, weeks)
        val counts = IntArray(weeks)
        sessions.distinctBy { it.id }.forEach { session ->
            val day = session.localEpochDay
            if (day in first..todayEpochDay) {
                counts[((mondayOf(day) - first) / DAYS_PER_WEEK).toInt()]++
            }
        }
        return counts.toList()
    }
}

/** Emits a fresh summary whenever a session opens or closes, and once on collection. */
class ObserveWeeklyTrainingSummaryUseCase(
    private val sessions: WorkoutSessionRepository,
    private val planHistory: PlanHistoryRepository,
    private val timeProvider: TimeProvider,
    private val periodization: PeriodizationConfig
) {
    operator fun invoke(): Flow<WeeklyTrainingSummary> = sessions.openFlow().map { build() }

    private suspend fun build(): WeeklyTrainingSummary {
        val today = localEpochDay(timeProvider.nowMillis(), timeProvider.utcOffsetMillis())
        val recent = sessions.sessionsFromLocalDay(WeeklyTrainingCounts.firstDay(today))
        val latestPlan = planHistory.latest()
        return WeeklyTrainingSummary(
            sessionsPerWeek = WeeklyTrainingCounts.sessionsPerWeek(recent, today),
            plannedDaysPerWeek = latestPlan?.days?.size?.takeIf { it > 0 },
            isDeloadWeek = latestPlan?.let { periodization.isDeload(it.weekNumber) } ?: false
        )
    }
}
