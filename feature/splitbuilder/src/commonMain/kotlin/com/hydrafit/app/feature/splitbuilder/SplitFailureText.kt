package com.hydrafit.app.feature.splitbuilder

import androidx.compose.runtime.Composable
import com.hydrafit.app.core.domain.schedule.ScheduleFailure
import hydrafit.feature.splitbuilder.generated.resources.Res
import hydrafit.feature.splitbuilder.generated.resources.split_failure_block_already_active
import hydrafit.feature.splitbuilder.generated.resources.split_failure_block_needs_workout
import hydrafit.feature.splitbuilder.generated.resources.split_failure_plan_needs_weekdays
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_no_weekday
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sentence for a refused schedule, in the app language. A reason this dialog has text for wins;
 * a failure without one is shown with the English detail it was thrown with, so nothing is hidden.
 */
@Composable
internal fun splitScheduleRejectedText(error: SplitScheduleError.Rejected): String =
    error.reason?.splitFailureResource()?.let { stringResource(it) } ?: error.detail.orEmpty()

/** The schedule reasons that confirming a generated plan can raise; the rest have no text here. */
private fun ScheduleFailure.splitFailureResource(): StringResource? = when (this) {
    ScheduleFailure.PICK_A_WEEKDAY -> Res.string.split_schedule_no_weekday
    ScheduleFailure.PLAN_NEEDS_WEEKDAYS -> Res.string.split_failure_plan_needs_weekdays
    ScheduleFailure.BLOCK_ALREADY_ACTIVE -> Res.string.split_failure_block_already_active
    ScheduleFailure.BLOCK_NEEDS_WORKOUT -> Res.string.split_failure_block_needs_workout
    else -> null
}
