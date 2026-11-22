package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable
import com.hydrafit.app.core.domain.schedule.ScheduleFailure
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_failure_entry_not_found
import hydrafit.feature.logger.generated.resources.logger_failure_finish_partial_cluster_first
import hydrafit.feature.logger.generated.resources.logger_failure_measure_change_unavailable
import hydrafit.feature.logger.generated.resources.logger_failure_measure_kept_by_cluster_shape
import hydrafit.feature.logger.generated.resources.logger_failure_measure_kept_by_ordered_targets
import hydrafit.feature.logger.generated.resources.logger_failure_measure_not_valid_for_slot
import hydrafit.feature.logger.generated.resources.logger_failure_not_active_guided_workout
import hydrafit.feature.logger.generated.resources.logger_failure_not_all_sets_recorded
import hydrafit.feature.logger.generated.resources.logger_failure_nothing_logged_skip_instead
import hydrafit.feature.logger.generated.resources.logger_failure_workout_already_finished
import hydrafit.feature.logger.generated.resources.logger_failure_workout_already_started
import hydrafit.feature.logger.generated.resources.logger_failure_workout_changed
import hydrafit.feature.logger.generated.resources.logger_failure_workout_has_recorded_sets
import hydrafit.feature.logger.generated.resources.logger_failure_workout_not_found
import hydrafit.feature.logger.generated.resources.logger_failure_workout_not_in_active_block
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sentence for a failure, in the app language. A reason this screen has text for wins; a
 * failure without one is shown with the English detail it was thrown with, so nothing is hidden.
 */
@Composable
internal fun LoggerFailure.text(): String {
    schedule?.loggerFailureResource()?.let { return stringResource(it) }
    return detail.orEmpty()
}

/**
 * The schedule reasons the workout logger can raise: finishing or skipping a workout, correcting a
 * slot's measure and guided targets. The others belong to block management and have no text here.
 */
private fun ScheduleFailure.loggerFailureResource(): StringResource? = when (this) {
    ScheduleFailure.WORKOUT_NOT_FOUND -> Res.string.logger_failure_workout_not_found
    ScheduleFailure.WORKOUT_ALREADY_FINISHED -> Res.string.logger_failure_workout_already_finished
    ScheduleFailure.WORKOUT_NOT_IN_ACTIVE_BLOCK ->
        Res.string.logger_failure_workout_not_in_active_block
    ScheduleFailure.WORKOUT_CHANGED -> Res.string.logger_failure_workout_changed
    ScheduleFailure.WORKOUT_ALREADY_STARTED -> Res.string.logger_failure_workout_already_started
    ScheduleFailure.WORKOUT_HAS_RECORDED_SETS ->
        Res.string.logger_failure_workout_has_recorded_sets
    ScheduleFailure.NOTHING_LOGGED_SKIP_INSTEAD ->
        Res.string.logger_failure_nothing_logged_skip_instead
    ScheduleFailure.NOT_ALL_SETS_RECORDED -> Res.string.logger_failure_not_all_sets_recorded
    ScheduleFailure.FINISH_PARTIAL_CLUSTER_FIRST ->
        Res.string.logger_failure_finish_partial_cluster_first
    ScheduleFailure.ENTRY_NOT_FOUND -> Res.string.logger_failure_entry_not_found
    ScheduleFailure.NOT_ACTIVE_GUIDED_WORKOUT ->
        Res.string.logger_failure_not_active_guided_workout
    ScheduleFailure.MEASURE_CHANGE_UNAVAILABLE ->
        Res.string.logger_failure_measure_change_unavailable
    ScheduleFailure.MEASURE_KEPT_BY_ORDERED_TARGETS ->
        Res.string.logger_failure_measure_kept_by_ordered_targets
    ScheduleFailure.MEASURE_KEPT_BY_CLUSTER_SHAPE ->
        Res.string.logger_failure_measure_kept_by_cluster_shape
    ScheduleFailure.MEASURE_NOT_VALID_FOR_SLOT ->
        Res.string.logger_failure_measure_not_valid_for_slot
    else -> null
}
