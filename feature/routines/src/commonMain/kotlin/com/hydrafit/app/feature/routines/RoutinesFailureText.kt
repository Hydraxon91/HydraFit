package com.hydrafit.app.feature.routines

import androidx.compose.runtime.Composable
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.routine.RoutineTemplateFailure
import com.hydrafit.app.core.domain.schedule.ScheduleFailure
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.routines_activation_no_weekday
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_block_already_active
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_block_needs_workout
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_block_not_found
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_only_active_block_can_finish
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_only_active_block_can_reschedule
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_only_pending_workout_can_move
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_routine_archived
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_routine_needs_workout
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_routine_unknown_exercise
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_workout_changed
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_workout_not_found
import hydrafit.feature.routines.generated.resources.routines_schedule_failure_workouts_still_pending
import hydrafit.feature.routines.generated.resources.routines_set_targets_error_reps
import hydrafit.feature.routines.generated.resources.routines_set_targets_error_sets
import hydrafit.feature.routines.generated.resources.routines_template_failure_bodyweight_cannot_carry_load
import hydrafit.feature.routines.generated.resources.routines_template_failure_cluster_needs_external_load
import hydrafit.feature.routines.generated.resources.routines_template_failure_cluster_needs_reps
import hydrafit.feature.routines.generated.resources.routines_template_failure_cluster_not_valid
import hydrafit.feature.routines.generated.resources.routines_template_failure_distance_not_a_number
import hydrafit.feature.routines.generated.resources.routines_template_failure_distance_not_positive
import hydrafit.feature.routines.generated.resources.routines_template_failure_distance_range
import hydrafit.feature.routines.generated.resources.routines_template_failure_force_cannot_carry_load
import hydrafit.feature.routines.generated.resources.routines_template_failure_force_not_a_number
import hydrafit.feature.routines.generated.resources.routines_template_failure_force_not_positive
import hydrafit.feature.routines.generated.resources.routines_template_failure_force_range
import hydrafit.feature.routines.generated.resources.routines_template_failure_ordered_keep_load_kind
import hydrafit.feature.routines.generated.resources.routines_template_failure_ordered_needs_reps
import hydrafit.feature.routines.generated.resources.routines_template_failure_ordered_not_valid
import hydrafit.feature.routines.generated.resources.routines_template_failure_ordered_target_count
import hydrafit.feature.routines.generated.resources.routines_template_failure_routine_in_use
import hydrafit.feature.routines.generated.resources.routines_template_failure_routine_name_blank
import hydrafit.feature.routines.generated.resources.routines_template_failure_routine_needs_workout
import hydrafit.feature.routines.generated.resources.routines_template_failure_routine_not_found
import hydrafit.feature.routines.generated.resources.routines_template_failure_routine_not_saved
import hydrafit.feature.routines.generated.resources.routines_template_failure_slot_needs_exercise
import hydrafit.feature.routines.generated.resources.routines_template_failure_slot_needs_one_measurement
import hydrafit.feature.routines.generated.resources.routines_template_failure_superset_not_valid
import hydrafit.feature.routines.generated.resources.routines_template_failure_weight_not_a_number
import hydrafit.feature.routines.generated.resources.routines_template_failure_weight_not_valid
import hydrafit.feature.routines.generated.resources.routines_template_failure_workout_name_blank
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sentence for a failure, in the app language. A reason this screen has text for wins; a
 * failure without one is shown with the English detail it was thrown with, so nothing is hidden.
 */
@Composable
internal fun RoutinesFailure.text(): String {
    template?.let { return templateFailureText(it) }
    schedule?.scheduleFailureResource()?.let { return stringResource(it) }
    return detail.orEmpty()
}

@Composable
private fun templateFailureText(reason: RoutineTemplateFailure): String = when (reason) {
    RoutineTemplateFailure.SETS_OUT_OF_RANGE -> stringResource(
        Res.string.routines_set_targets_error_sets,
        PrescriptionBounds.MIN_SETS,
        PrescriptionBounds.MAX_SETS
    )
    RoutineTemplateFailure.REPS_OUT_OF_RANGE -> stringResource(
        Res.string.routines_set_targets_error_reps,
        PrescriptionBounds.MIN_REPS,
        PrescriptionBounds.MAX_REPS
    )
    RoutineTemplateFailure.DISTANCE_OUT_OF_RANGE -> stringResource(
        Res.string.routines_template_failure_distance_range,
        PrescriptionBounds.MIN_DISTANCE_METERS,
        PrescriptionBounds.MAX_DISTANCE_METERS
    )
    RoutineTemplateFailure.FORCE_OUT_OF_RANGE -> stringResource(
        Res.string.routines_template_failure_force_range,
        PrescriptionBounds.MIN_FORCE_NEWTONS.toString(),
        PrescriptionBounds.MAX_FORCE_NEWTONS.toInt().toString()
    )
    RoutineTemplateFailure.ROUTINE_NAME_BLANK -> stringResource(
        Res.string.routines_template_failure_routine_name_blank
    )
    RoutineTemplateFailure.ROUTINE_NEEDS_WORKOUT -> stringResource(
        Res.string.routines_template_failure_routine_needs_workout
    )
    RoutineTemplateFailure.WORKOUT_NAME_BLANK -> stringResource(
        Res.string.routines_template_failure_workout_name_blank
    )
    RoutineTemplateFailure.ROUTINE_NOT_FOUND -> stringResource(
        Res.string.routines_template_failure_routine_not_found
    )
    RoutineTemplateFailure.ROUTINE_NOT_SAVED -> stringResource(
        Res.string.routines_template_failure_routine_not_saved
    )
    RoutineTemplateFailure.ROUTINE_IN_USE -> stringResource(
        Res.string.routines_template_failure_routine_in_use
    )
    RoutineTemplateFailure.SLOT_NEEDS_EXERCISE -> stringResource(
        Res.string.routines_template_failure_slot_needs_exercise
    )
    RoutineTemplateFailure.SLOT_NEEDS_ONE_MEASUREMENT -> stringResource(
        Res.string.routines_template_failure_slot_needs_one_measurement
    )
    RoutineTemplateFailure.WEIGHT_NOT_VALID -> stringResource(
        Res.string.routines_template_failure_weight_not_valid
    )
    RoutineTemplateFailure.WEIGHT_NOT_A_NUMBER -> stringResource(
        Res.string.routines_template_failure_weight_not_a_number
    )
    RoutineTemplateFailure.DISTANCE_NOT_A_NUMBER -> stringResource(
        Res.string.routines_template_failure_distance_not_a_number
    )
    RoutineTemplateFailure.DISTANCE_NOT_POSITIVE -> stringResource(
        Res.string.routines_template_failure_distance_not_positive
    )
    RoutineTemplateFailure.FORCE_NOT_A_NUMBER -> stringResource(
        Res.string.routines_template_failure_force_not_a_number
    )
    RoutineTemplateFailure.FORCE_NOT_POSITIVE -> stringResource(
        Res.string.routines_template_failure_force_not_positive
    )
    RoutineTemplateFailure.FORCE_CANNOT_CARRY_LOAD -> stringResource(
        Res.string.routines_template_failure_force_cannot_carry_load
    )
    RoutineTemplateFailure.BODYWEIGHT_CANNOT_CARRY_LOAD -> stringResource(
        Res.string.routines_template_failure_bodyweight_cannot_carry_load
    )
    RoutineTemplateFailure.SUPERSET_NOT_VALID -> stringResource(
        Res.string.routines_template_failure_superset_not_valid
    )
    RoutineTemplateFailure.CLUSTER_NEEDS_REPS -> stringResource(
        Res.string.routines_template_failure_cluster_needs_reps
    )
    RoutineTemplateFailure.CLUSTER_NEEDS_EXTERNAL_LOAD -> stringResource(
        Res.string.routines_template_failure_cluster_needs_external_load
    )
    RoutineTemplateFailure.CLUSTER_NOT_VALID -> stringResource(
        Res.string.routines_template_failure_cluster_not_valid
    )
    RoutineTemplateFailure.ORDERED_NEEDS_REPS -> stringResource(
        Res.string.routines_template_failure_ordered_needs_reps
    )
    RoutineTemplateFailure.ORDERED_NOT_VALID -> stringResource(
        Res.string.routines_template_failure_ordered_not_valid
    )
    RoutineTemplateFailure.ORDERED_TARGET_COUNT -> stringResource(
        Res.string.routines_template_failure_ordered_target_count
    )
    RoutineTemplateFailure.ORDERED_KEEP_LOAD_KIND -> stringResource(
        Res.string.routines_template_failure_ordered_keep_load_kind
    )
}

/**
 * The schedule reasons this screen can raise. The others belong to the workout logger and have no
 * text here, so they fall back to the English detail.
 */
private fun ScheduleFailure.scheduleFailureResource(): StringResource? = when (this) {
    ScheduleFailure.PICK_A_WEEKDAY -> Res.string.routines_activation_no_weekday
    ScheduleFailure.BLOCK_NOT_FOUND -> Res.string.routines_schedule_failure_block_not_found
    ScheduleFailure.BLOCK_ALREADY_ACTIVE ->
        Res.string.routines_schedule_failure_block_already_active
    ScheduleFailure.BLOCK_NEEDS_WORKOUT -> Res.string.routines_schedule_failure_block_needs_workout
    ScheduleFailure.ROUTINE_ARCHIVED -> Res.string.routines_schedule_failure_routine_archived
    ScheduleFailure.ROUTINE_NEEDS_WORKOUT ->
        Res.string.routines_schedule_failure_routine_needs_workout
    ScheduleFailure.ROUTINE_UNKNOWN_EXERCISE ->
        Res.string.routines_schedule_failure_routine_unknown_exercise
    ScheduleFailure.ONLY_ACTIVE_BLOCK_CAN_FINISH ->
        Res.string.routines_schedule_failure_only_active_block_can_finish
    ScheduleFailure.WORKOUTS_STILL_PENDING ->
        Res.string.routines_schedule_failure_workouts_still_pending
    ScheduleFailure.ONLY_PENDING_WORKOUT_CAN_MOVE ->
        Res.string.routines_schedule_failure_only_pending_workout_can_move
    ScheduleFailure.ONLY_ACTIVE_BLOCK_CAN_RESCHEDULE ->
        Res.string.routines_schedule_failure_only_active_block_can_reschedule
    ScheduleFailure.WORKOUT_NOT_FOUND -> Res.string.routines_schedule_failure_workout_not_found
    ScheduleFailure.WORKOUT_CHANGED -> Res.string.routines_schedule_failure_workout_changed
    else -> null
}
