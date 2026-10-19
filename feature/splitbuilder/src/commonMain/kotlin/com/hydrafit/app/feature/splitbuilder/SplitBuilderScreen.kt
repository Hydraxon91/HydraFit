package com.hydrafit.app.feature.splitbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmCoverageUnmetReason
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlanExplanation
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SwapCandidate
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.isoDateUtc
import com.hydrafit.app.core.domain.time.isoLocalDateTime
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import hydrafit.feature.splitbuilder.generated.resources.Res
import hydrafit.feature.splitbuilder.generated.resources.engine_label_deterministic
import hydrafit.feature.splitbuilder.generated.resources.engine_label_gemini
import hydrafit.feature.splitbuilder.generated.resources.engine_label_local_llm
import hydrafit.feature.splitbuilder.generated.resources.focus_full_body
import hydrafit.feature.splitbuilder.generated.resources.focus_legs
import hydrafit.feature.splitbuilder.generated.resources.focus_lower
import hydrafit.feature.splitbuilder.generated.resources.focus_pull
import hydrafit.feature.splitbuilder.generated.resources.focus_push
import hydrafit.feature.splitbuilder.generated.resources.focus_upper
import hydrafit.feature.splitbuilder.generated.resources.nav_label
import hydrafit.feature.splitbuilder.generated.resources.split_accept_plan
import hydrafit.feature.splitbuilder.generated.resources.split_accessory_sets_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_apply
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_clear
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_edit
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_error_reps
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_error_rir
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_max_reps_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_min_reps_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_off
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_rir_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_shape_ascending_load
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_shape_descending_load
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_shape_label
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_shape_pyramid
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_shape_straight
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_summary
import hydrafit.feature.splitbuilder.generated.resources.split_auto_targets_title
import hydrafit.feature.splitbuilder.generated.resources.split_builder_title
import hydrafit.feature.splitbuilder.generated.resources.split_cancel
import hydrafit.feature.splitbuilder.generated.resources.split_confirm
import hydrafit.feature.splitbuilder.generated.resources.split_day
import hydrafit.feature.splitbuilder.generated.resources.split_days_label
import hydrafit.feature.splitbuilder.generated.resources.split_delete_plan
import hydrafit.feature.splitbuilder.generated.resources.split_deload_week
import hydrafit.feature.splitbuilder.generated.resources.split_error
import hydrafit.feature.splitbuilder.generated.resources.split_error_api_key
import hydrafit.feature.splitbuilder.generated.resources.split_error_invalid_request
import hydrafit.feature.splitbuilder.generated.resources.split_error_invalid_response
import hydrafit.feature.splitbuilder.generated.resources.split_error_network
import hydrafit.feature.splitbuilder.generated.resources.split_error_no_eligible_exercises
import hydrafit.feature.splitbuilder.generated.resources.split_error_no_usable_exercises
import hydrafit.feature.splitbuilder.generated.resources.split_error_quota_exhausted
import hydrafit.feature.splitbuilder.generated.resources.split_error_rate_limited
import hydrafit.feature.splitbuilder.generated.resources.split_error_service_unavailable
import hydrafit.feature.splitbuilder.generated.resources.split_error_timeout
import hydrafit.feature.splitbuilder.generated.resources.split_error_transient
import hydrafit.feature.splitbuilder.generated.resources.split_exercise_line
import hydrafit.feature.splitbuilder.generated.resources.split_exercise_line_timed
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_accepted
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_bounded_by_progression
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_capped_by_equipment
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_ceiling_not_binding
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_deload_scale
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_deload_scaled
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_equipment_ceiling
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_not_accepted
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_ai_proposed
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_baseline_effective
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_baseline_manual_floor
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_baseline_qualifying_sets
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_baseline_recent_estimate
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_before_ceiling
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_ceiling
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_ceiling_binds
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_deload_scale
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_intensity
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_reps
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_conversion_rounded_to
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_details
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_hide_details
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_kilograms
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_line
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_plan_attribution_ai
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_plan_attribution_deterministic
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_plan_deload_scales
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_applied
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_delta
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_failure_streak_size
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_held_after_misses
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_none_no_prescription
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_none_no_streak
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_paused_deload
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_progression_success_streak_size
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_sets_base
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_sets_deload_scale
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_sets_final
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_sets_reduced_for_fatigue
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_substituted
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_summary_ai
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_summary_calculated
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_summary_sets_only
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_unavailable_ai_not_shared
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_unavailable_ai_omitted
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_unavailable_ai_rejected
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_unavailable_insufficient_evidence
import hydrafit.feature.splitbuilder.generated.resources.split_explanation_unavailable_not_load_tracked
import hydrafit.feature.splitbuilder.generated.resources.split_fallback_invalid_response
import hydrafit.feature.splitbuilder.generated.resources.split_fallback_note
import hydrafit.feature.splitbuilder.generated.resources.split_generated_by
import hydrafit.feature.splitbuilder.generated.resources.split_generated_routine_name
import hydrafit.feature.splitbuilder.generated.resources.split_history
import hydrafit.feature.splitbuilder.generated.resources.split_history_entry
import hydrafit.feature.splitbuilder.generated.resources.split_loading
import hydrafit.feature.splitbuilder.generated.resources.split_loading_progress
import hydrafit.feature.splitbuilder.generated.resources.split_measure_action
import hydrafit.feature.splitbuilder.generated.resources.split_measure_dialog_title
import hydrafit.feature.splitbuilder.generated.resources.split_measure_error_reps
import hydrafit.feature.splitbuilder.generated.resources.split_measure_error_seconds
import hydrafit.feature.splitbuilder.generated.resources.split_measure_error_seconds_large
import hydrafit.feature.splitbuilder.generated.resources.split_measure_reps
import hydrafit.feature.splitbuilder.generated.resources.split_measure_seconds
import hydrafit.feature.splitbuilder.generated.resources.split_muscle_biceps
import hydrafit.feature.splitbuilder.generated.resources.split_muscle_triceps
import hydrafit.feature.splitbuilder.generated.resources.split_plan_accepted
import hydrafit.feature.splitbuilder.generated.resources.split_plan_export_unsupported
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_clear
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_clock
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_disclosure
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_edit
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_label
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_not_block
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_past
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_preview
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_rejected
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_shift
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_stale
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_title
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_unknown
import hydrafit.feature.splitbuilder.generated.resources.split_planning_dates_unknown_note
import hydrafit.feature.splitbuilder.generated.resources.split_regenerate
import hydrafit.feature.splitbuilder.generated.resources.split_retry
import hydrafit.feature.splitbuilder.generated.resources.split_review_equipment
import hydrafit.feature.splitbuilder.generated.resources.split_routine_saved
import hydrafit.feature.splitbuilder.generated.resources.split_routine_saved_view
import hydrafit.feature.splitbuilder.generated.resources.split_save_routine
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_choose_date
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_dates_stale
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_frequency
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_mode_sequence
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_mode_weekday
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_no_weekday
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_preview
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_replace
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_start_today
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_title
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_weekdays
import hydrafit.feature.splitbuilder.generated.resources.split_sets_label
import hydrafit.feature.splitbuilder.generated.resources.split_start_block
import hydrafit.feature.splitbuilder.generated.resources.split_suggested_weight
import hydrafit.feature.splitbuilder.generated.resources.split_superset_enabled
import hydrafit.feature.splitbuilder.generated.resources.split_superset_error
import hydrafit.feature.splitbuilder.generated.resources.split_superset_label
import hydrafit.feature.splitbuilder.generated.resources.split_superset_note
import hydrafit.feature.splitbuilder.generated.resources.split_superset_pair_rest
import hydrafit.feature.splitbuilder.generated.resources.split_superset_transition
import hydrafit.feature.splitbuilder.generated.resources.split_swap_action
import hydrafit.feature.splitbuilder.generated.resources.split_swap_candidate
import hydrafit.feature.splitbuilder.generated.resources.split_swap_dialog_cancel
import hydrafit.feature.splitbuilder.generated.resources.split_swap_dialog_empty
import hydrafit.feature.splitbuilder.generated.resources.split_swap_dialog_title
import hydrafit.feature.splitbuilder.generated.resources.split_swap_no_candidates
import hydrafit.feature.splitbuilder.generated.resources.split_volume_ai
import hydrafit.feature.splitbuilder.generated.resources.split_volume_deterministic
import hydrafit.feature.splitbuilder.generated.resources.split_volume_direct_label
import hydrafit.feature.splitbuilder.generated.resources.split_volume_explanation_title
import hydrafit.feature.splitbuilder.generated.resources.split_volume_indirect_label
import hydrafit.feature.splitbuilder.generated.resources.split_volume_not_enforced
import hydrafit.feature.splitbuilder.generated.resources.split_volume_soreness_constraint
import hydrafit.feature.splitbuilder.generated.resources.split_volume_substituted
import hydrafit.feature.splitbuilder.generated.resources.split_volume_unmet_available
import hydrafit.feature.splitbuilder.generated.resources.split_volume_unmet_candidate
import hydrafit.feature.splitbuilder.generated.resources.split_volume_unmet_fatigue
import hydrafit.feature.splitbuilder.generated.resources.split_week
import hydrafit.feature.splitbuilder.generated.resources.weekday_friday
import hydrafit.feature.splitbuilder.generated.resources.weekday_monday
import hydrafit.feature.splitbuilder.generated.resources.weekday_saturday
import hydrafit.feature.splitbuilder.generated.resources.weekday_sunday
import hydrafit.feature.splitbuilder.generated.resources.weekday_thursday
import hydrafit.feature.splitbuilder.generated.resources.weekday_tuesday
import hydrafit.feature.splitbuilder.generated.resources.weekday_wednesday
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

val splitBuilderRoute: String = "plan"

val splitBuilderDestination: FeatureDestination = FeatureDestination(
    route = splitBuilderRoute,
    label = Res.string.nav_label,
    graph = { { splitBuilderGraph() } }
)

private val dayOptions = 2..6
private val setOptions = 2..6

fun NavGraphBuilder.splitBuilderGraph() {
    composable(splitBuilderRoute) { SplitBuilderRoute() }
}

@Composable
fun SplitBuilderRoute(
    modifier: Modifier = Modifier,
    viewModel: SplitBuilderViewModel = koinViewModel(),
    exportViewModel: PlanExportViewModel = koinViewModel()
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshContext() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportState by exportViewModel.state.collectAsStateWithLifecycle()
    val weightUnit by koinInject<WeightUnitRepository>().unitFlow()
        .collectAsStateWithLifecycle(initialValue = WeightUnit.KG)
    val planProgress by koinInject<OnDevicePlanProgressReporter>().progress
        .collectAsStateWithLifecycle()
    val timeProvider = koinInject<TimeProvider>()
    val snackbarHostState = remember { SnackbarHostState() }
    val openTab = LocalAppTabNavigator.current
    val savedMessage = stringResource(Res.string.split_routine_saved)
    val viewRoutinesAction = stringResource(Res.string.split_routine_saved_view)
    val generatedRoutineName = stringResource(Res.string.split_generated_routine_name)

    // Derived when a transfer action runs, so the name matches the plan on screen at that moment
    // rather than the composition that created the button.
    fun planTransferName(): String = "$generatedRoutineName " + isoLocalDateTime(
        timeProvider.nowMillis(),
        timeProvider.utcOffsetMillis()
    )

    LaunchedEffect(state.routineSaved) {
        if (state.routineSaved) {
            val result = snackbarHostState.showSnackbar(
                message = savedMessage,
                actionLabel = viewRoutinesAction,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) openTab(AppTab.ROUTINES)
            viewModel.onRoutineSavedShown()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        SplitBuilderScreen(
            state = state,
            weightUnit = weightUnit,
            planProgress = planProgress,
            onDaysPerWeekSelected = viewModel::onDaysPerWeekSelected,
            onSetsPerExerciseChanged = viewModel::onSetsPerExerciseChanged,
            onAccessorySetsPerExerciseChanged = viewModel::onAccessorySetsPerExerciseChanged,
            onAutomaticSetPrescriptionRequested = viewModel::onAutomaticSetPrescriptionRequested,
            onAutomaticSetPrescriptionCleared = viewModel::onAutomaticSetPrescriptionCleared,
            onPlannerSupersetEnabledChanged = viewModel::onPlannerSupersetEnabledChanged,
            onPlannerSupersetTransitionChanged = viewModel::onPlannerSupersetTransitionChanged,
            onPlannerSupersetPairRestChanged = viewModel::onPlannerSupersetPairRestChanged,
            onEditPlanningDates = viewModel::onPlanningDatesRequested,
            onAcceptPlan = viewModel::onAcceptPlan,
            onSchedulePlan = viewModel::onScheduleRequested,
            onSaveAsRoutine = { viewModel.onSaveAsRoutine(planTransferName()) },
            onRegenerate = viewModel::refresh,
            onViewAcceptedPlan = viewModel::onViewAcceptedPlan,
            onDeletePlan = viewModel::onDeletePlan,
            onSwapRequested = viewModel::onSwapRequested,
            onSwapCandidateSelected = viewModel::onSwapCandidateSelected,
            onSwapDialogDismissed = viewModel::onSwapDialogDismissed,
            onMeasureRequested = viewModel::onMeasureRequested,
            onMeasureMetricChanged = viewModel::onMeasureMetricChanged,
            onMeasureValueChanged = viewModel::onMeasureValueChanged,
            onMeasureConfirmed = viewModel::onMeasureConfirmed,
            onMeasureDialogDismissed = viewModel::onMeasureDialogDismissed,
            onRetry = viewModel::refresh,
            onReviewEquipment = { openTab(AppTab.EQUIPMENT) },
            exportState = exportState,
            onExportIncludeWeightsChanged = exportViewModel::onIncludeWeightsChanged,
            onExportPlanJson = { handle ->
                state.plan?.let { exportViewModel.exportJson(it, planTransferName(), handle) }
            },
            onExportPlanPdf = { handle ->
                state.plan?.let { exportViewModel.printPdf(it, planTransferName(), handle) }
            },
            onExportStatusDismissed = exportViewModel::onStatusDismissed,
            modifier = Modifier.fillMaxSize()
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    state.scheduleDialog?.let { dialog ->
        SplitScheduleDialog(dialog = dialog, viewModel = viewModel)
    }
    state.planningDatesDialog?.let { dialog ->
        PlanningDatesDialog(dialog = dialog, viewModel = viewModel)
    }
    state.automaticSetPrescriptionDialog?.let { dialog ->
        AutomaticSetPrescriptionDialog(dialog = dialog, viewModel = viewModel)
    }
}

@Composable
fun SplitBuilderScreen(
    state: SplitBuilderUiState,
    weightUnit: WeightUnit,
    planProgress: OnDevicePlanProgress?,
    onDaysPerWeekSelected: (Int) -> Unit,
    onSetsPerExerciseChanged: (Int) -> Unit,
    onAccessorySetsPerExerciseChanged: (Int) -> Unit,
    onAutomaticSetPrescriptionRequested: () -> Unit = {},
    onAutomaticSetPrescriptionCleared: () -> Unit = {},
    onPlannerSupersetEnabledChanged: (Boolean) -> Unit = {},
    onPlannerSupersetTransitionChanged: (String) -> Unit = {},
    onPlannerSupersetPairRestChanged: (String) -> Unit = {},
    onEditPlanningDates: () -> Unit,
    onAcceptPlan: () -> Unit,
    onSchedulePlan: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onRegenerate: () -> Unit,
    onViewAcceptedPlan: (AcceptedPlan) -> Unit,
    onDeletePlan: (AcceptedPlan) -> Unit,
    onSwapRequested: (Int, Int) -> Unit,
    onSwapCandidateSelected: (String) -> Unit,
    onSwapDialogDismissed: () -> Unit,
    onMeasureRequested: (Int, Int) -> Unit,
    onMeasureMetricChanged: (MeasureMetric) -> Unit,
    onMeasureValueChanged: (String) -> Unit,
    onMeasureConfirmed: () -> Unit,
    onMeasureDialogDismissed: () -> Unit,
    onRetry: () -> Unit,
    onReviewEquipment: () -> Unit = {},
    exportState: PlanExportUiState = PlanExportUiState(),
    onExportIncludeWeightsChanged: (Boolean) -> Unit = {},
    onExportPlanJson: (String) -> Unit = {},
    onExportPlanPdf: (String) -> Unit = {},
    onExportStatusDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(Res.string.split_builder_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(text = stringResource(Res.string.split_days_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dayOptions.forEach { days ->
                FilterChip(
                    selected = state.daysPerWeek == days,
                    onClick = { onDaysPerWeekSelected(days) },
                    label = { Text(days.toString()) }
                )
            }
        }
        Text(text = stringResource(Res.string.split_sets_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            setOptions.forEach { sets ->
                FilterChip(
                    selected = state.setsPerExercise == sets,
                    onClick = { onSetsPerExerciseChanged(sets) },
                    label = { Text(sets.toString()) }
                )
            }
        }
        Text(text = stringResource(Res.string.split_accessory_sets_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            setOptions.forEach { sets ->
                FilterChip(
                    selected = state.accessorySetsPerExercise == sets,
                    onClick = { onAccessorySetsPerExerciseChanged(sets) },
                    label = { Text(sets.toString()) }
                )
            }
        }
        Text(text = stringResource(Res.string.split_auto_targets_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = state.automaticSetPrescription?.let { automaticSetPrescriptionSummary(it) }
                    ?: stringResource(Res.string.split_auto_targets_off)
            )
            TextButton(onClick = onAutomaticSetPrescriptionRequested) {
                Text(stringResource(Res.string.split_auto_targets_edit))
            }
            if (state.automaticSetPrescription != null) {
                TextButton(onClick = onAutomaticSetPrescriptionCleared) {
                    Text(stringResource(Res.string.split_auto_targets_clear))
                }
            }
        }
        Text(text = stringResource(Res.string.split_superset_label))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = state.plannerSupersetEnabled,
                onCheckedChange = onPlannerSupersetEnabledChanged
            )
            Text(stringResource(Res.string.split_superset_enabled))
        }
        if (state.plannerSupersetEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.plannerSupersetTransitionSeconds,
                    onValueChange = onPlannerSupersetTransitionChanged,
                    label = { Text(stringResource(Res.string.split_superset_transition)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.plannerSupersetPairRestSeconds,
                    onValueChange = onPlannerSupersetPairRestChanged,
                    label = { Text(stringResource(Res.string.split_superset_pair_rest)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            if (plannerSupersetPreference(
                    enabled = true,
                    transitionSeconds = state.plannerSupersetTransitionSeconds,
                    pairRestSeconds = state.plannerSupersetPairRestSeconds
                ) == null
            ) {
                Text(
                    text = stringResource(Res.string.split_superset_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = stringResource(Res.string.split_superset_note),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = stringResource(Res.string.split_planning_dates_label))
        if (state.planningDates.dates.isEmpty()) {
            Text(
                text = stringResource(Res.string.split_planning_dates_unknown),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            state.planningDates.dates.forEach { day ->
                Text(
                    text = isoDateUtc(day * MILLIS_PER_DAY),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.planningDatesRejected) {
            Text(
                text = stringResource(Res.string.split_planning_dates_rejected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        TextButton(onClick = onEditPlanningDates) {
            Text(stringResource(Res.string.split_planning_dates_edit))
        }
        if (state.isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(Res.string.split_loading))
            }
            planProgress?.let { progress ->
                Text(
                    text = stringResource(
                        Res.string.split_loading_progress,
                        progress.tokensGenerated,
                        progress.expectedTokens,
                        formatTokensPerSecond(progress.tokensPerSecond)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (state.hasError) {
            val errorText = state.failureReason.reasonMessage()
                ?: if (state.isTransientError) {
                    Res.string.split_error_transient
                } else {
                    Res.string.split_error
                }
            Text(
                text = stringResource(errorText),
                style = MaterialTheme.typography.bodyMedium
            )
            state.errorDetail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.split_retry))
            }
            if (state.failureReason == PlanFailureReason.NO_ELIGIBLE_EXERCISES) {
                TextButton(onClick = onReviewEquipment) {
                    Text(stringResource(Res.string.split_review_equipment))
                }
            }
        }
        state.plan?.let { plan ->
            Text(
                text = stringResource(
                    Res.string.split_week,
                    plan.weekNumber,
                    plan.cycleNumber
                ),
                style = MaterialTheme.typography.titleSmall
            )
            if (PeriodizationConfig().isDeload(plan.weekNumber)) {
                Text(
                    text = stringResource(Res.string.split_deload_week),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Text(
                text = stringResource(
                    Res.string.split_generated_by,
                    stringResource(plan.engine.labelResource())
                ),
                style = MaterialTheme.typography.labelLarge
            )
            plan.explanation?.let { explanation ->
                PlanExplanationContext(explanation)
            }
            VolumeExplanationCard(
                coverage = plan.armCoverage,
                attribution = if (plan.engine == PlannerEngineId.DETERMINISTIC) {
                    PlanAttribution.DETERMINISTIC
                } else {
                    PlanAttribution.AI_GENERATED
                },
                invalidated = state.volumeExplanationInvalidated
            )
            if (state.isPlanAccepted) {
                Text(
                    text = stringResource(Res.string.split_plan_accepted),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Button(onClick = onAcceptPlan, enabled = !state.draftContextStale) {
                    Text(stringResource(Res.string.split_accept_plan))
                }
            }
            if (state.draftContextStale) {
                Text(
                    text = stringResource(Res.string.split_planning_dates_stale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSchedulePlan, enabled = !state.draftContextStale) {
                    Text(stringResource(Res.string.split_start_block))
                }
                TextButton(onClick = onSaveAsRoutine) {
                    Text(stringResource(Res.string.split_save_routine))
                }
            }
            Button(onClick = onRegenerate, enabled = state.canRegenerate) {
                Text(stringResource(Res.string.split_regenerate))
            }
            if (exportState.jsonSupported) {
                PlanExportSection(
                    state = exportState,
                    onIncludeWeightsChanged = onExportIncludeWeightsChanged,
                    onExportJson = onExportPlanJson,
                    onPrintPdf = onExportPlanPdf,
                    onStatusDismissed = onExportStatusDismissed
                )
            } else {
                Text(
                    text = stringResource(Res.string.split_plan_export_unsupported),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.usedFallbackEngine) {
                state.requestedEngine?.let { requested ->
                    Text(
                        text = stringResource(
                            Res.string.split_fallback_note,
                            stringResource(requested.labelResource())
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.fallbackReason == PlanFailureReason.INVALID_RESPONSE) {
                    Text(
                        text = stringResource(Res.string.split_fallback_invalid_response),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            plan.days.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.split_day, day.dayIndex + 1) +
                            " - " + stringResource(day.focus.labelResource()),
                        style = MaterialTheme.typography.titleMedium
                    )
                    day.exercises.forEachIndexed { position, exercise ->
                        Column {
                            val name = state.exerciseNames[exercise.exerciseId]
                                ?: exercise.exerciseId
                            val weight = exercise.suggestedWeightKg?.let { kg ->
                                stringResource(
                                    Res.string.split_suggested_weight,
                                    formatWeight(weightUnit.kilogramsToDisplay(kg)),
                                    weightUnit.label
                                )
                            }.orEmpty()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = exerciseLine(
                                        name = name,
                                        sets = exercise.sets,
                                        reps = exercise.reps,
                                        durationSeconds = exercise.durationSeconds,
                                        weight = weight
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onMeasureRequested(day.dayIndex, position) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = stringResource(
                                            Res.string.split_measure_action,
                                            name
                                        )
                                    )
                                }
                                if (state.isPlanAccepted) {
                                    IconButton(
                                        onClick = { onSwapRequested(day.dayIndex, position) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = stringResource(
                                                Res.string.split_swap_action
                                            )
                                        )
                                    }
                                }
                            }
                            TargetExplanationDisclosure(
                                key = day.dayIndex to position,
                                explanation = exercise.explanation,
                                substituted = plan.explanation != null &&
                                    exercise.explanation == null,
                                weightUnit = weightUnit
                            )
                        }
                    }
                }
            }
        }
        if (state.swapDialogOpen) {
            val target = state.plan?.days
                ?.firstOrNull { it.dayIndex == state.swapTargetDayIndex }
                ?.exercises
                ?.getOrNull(state.swapTargetPosition ?: -1)
            val targetName = target
                ?.let { state.exerciseNames[it.exerciseId] ?: it.exerciseId }
                .orEmpty()
            SwapCandidateDialog(
                targetName = targetName,
                candidates = state.swapCandidates,
                noCandidates = state.swapNoCandidates,
                weightUnit = weightUnit,
                onSelect = onSwapCandidateSelected,
                onDismiss = onSwapDialogDismissed
            )
        }
        state.measureDialog?.let { dialog ->
            val target = state.plan?.days
                ?.firstOrNull { it.dayIndex == dialog.dayIndex }
                ?.exercises
                ?.getOrNull(dialog.position)
            val targetName = target
                ?.let { state.exerciseNames[it.exerciseId] ?: it.exerciseId }
                .orEmpty()
            MeasureDialog(
                dialog = dialog,
                targetName = targetName,
                onMetricChanged = onMeasureMetricChanged,
                onValueChanged = onMeasureValueChanged,
                onConfirm = onMeasureConfirmed,
                onDismiss = onMeasureDialogDismissed
            )
        }
        if (state.history.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.split_history),
                style = MaterialTheme.typography.titleMedium
            )
            state.history.forEach { accepted ->
                val summary = stringResource(
                    Res.string.split_history_entry,
                    isoDateUtc(accepted.acceptedAtMillis),
                    stringResource(accepted.engine.labelResource()),
                    accepted.days.size,
                    accepted.weekNumber
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onViewAcceptedPlan(accepted) },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { onDeletePlan(accepted) }) {
                        Text(stringResource(Res.string.split_delete_plan))
                    }
                }
            }
        }
    }
}

/**
 * The slot line: `name  sets x reps  weight` for counted work and `name  sets × seconds s  weight`
 * for a timed slot, so seconds are never shown as a rep count. A slot with no measure is impossible
 * under the model's exactly-one invariant; it falls back to the bare name rather than a `0` sentinel.
 */
@Composable
private fun exerciseLine(
    name: String,
    sets: Int,
    reps: Int?,
    durationSeconds: Int?,
    weight: String
): String = when {
    durationSeconds != null -> stringResource(
        Res.string.split_exercise_line_timed,
        name,
        sets,
        durationSeconds,
        weight
    )
    reps != null -> stringResource(Res.string.split_exercise_line, name, sets, reps, weight)
    else -> name
}

@Composable
private fun MeasureDialog(
    dialog: MeasureDialogState,
    targetName: String,
    onMetricChanged: (MeasureMetric) -> Unit,
    onValueChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val metricLabel = if (dialog.metric == MeasureMetric.SECONDS) {
        Res.string.split_measure_seconds
    } else {
        Res.string.split_measure_reps
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.split_measure_dialog_title, targetName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialog.metric == MeasureMetric.REPS,
                        onClick = { onMetricChanged(MeasureMetric.REPS) },
                        label = { Text(stringResource(Res.string.split_measure_reps)) }
                    )
                    FilterChip(
                        selected = dialog.metric == MeasureMetric.SECONDS,
                        onClick = { onMetricChanged(MeasureMetric.SECONDS) },
                        label = { Text(stringResource(Res.string.split_measure_seconds)) }
                    )
                }
                OutlinedTextField(
                    value = dialog.value,
                    onValueChange = onValueChanged,
                    label = { Text(stringResource(metricLabel)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = dialog.error != null
                )
                dialog.error?.let { error ->
                    Text(
                        text = stringResource(error.labelResource()),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.split_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.split_cancel))
            }
        }
    )
}

private fun MeasureError.labelResource(): StringResource = when (this) {
    MeasureError.REPS_RANGE -> Res.string.split_measure_error_reps
    MeasureError.SECONDS_RANGE -> Res.string.split_measure_error_seconds
    MeasureError.SECONDS_TOO_LARGE -> Res.string.split_measure_error_seconds_large
}

@Composable
private fun SwapCandidateDialog(
    targetName: String,
    candidates: List<SwapCandidate>,
    noCandidates: Boolean,
    weightUnit: WeightUnit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.split_swap_dialog_title, targetName)) },
        text = {
            when {
                noCandidates -> Text(stringResource(Res.string.split_swap_no_candidates))
                candidates.isEmpty() -> Text(stringResource(Res.string.split_swap_dialog_empty))
                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { candidate ->
                        val weight = candidate.suggestedWeightKg?.let { kg ->
                            stringResource(
                                Res.string.split_suggested_weight,
                                formatWeight(weightUnit.kilogramsToDisplay(kg)),
                                weightUnit.label
                            )
                        }.orEmpty()
                        TextButton(onClick = { onSelect(candidate.exerciseId) }) {
                            Text(
                                text = stringResource(
                                    Res.string.split_swap_candidate,
                                    candidate.name,
                                    weight
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.split_swap_dialog_cancel))
            }
        }
    )
}

private fun PlannerEngineId.labelResource(): StringResource = when (this) {
    PlannerEngineId.DETERMINISTIC -> Res.string.engine_label_deterministic
    PlannerEngineId.GEMINI_API -> Res.string.engine_label_gemini
    PlannerEngineId.LOCAL_LLM -> Res.string.engine_label_local_llm
}

private fun SplitFocus.labelResource(): StringResource = when (this) {
    SplitFocus.PUSH -> Res.string.focus_push
    SplitFocus.PULL -> Res.string.focus_pull
    SplitFocus.LEGS -> Res.string.focus_legs
    SplitFocus.UPPER -> Res.string.focus_upper
    SplitFocus.LOWER -> Res.string.focus_lower
    SplitFocus.FULL_BODY -> Res.string.focus_full_body
}

/** One decimal place, e.g. "8.4"; the progress line only needs a rough rate. */
private fun formatTokensPerSecond(value: Double): String =
    ((value * 10.0).roundToInt() / 10.0).toString()

/**
 * What produced a slot's target, with the recorded facts behind a disclosure. A slot with no record
 * shows the substitution note only when a confirmed swap cleared its reasons; otherwise it says
 * nothing rather than implying a reason was lost.
 */
@Composable
private fun TargetExplanationDisclosure(
    key: Any,
    explanation: ExerciseTargetExplanation?,
    substituted: Boolean,
    weightUnit: WeightUnit
) {
    val view = explanation?.toExplanationView()
    if (view == null) {
        if (substituted) {
            Text(
                text = stringResource(Res.string.split_explanation_substituted),
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }
    var expanded by remember(key) { mutableStateOf(false) }
    Text(
        text = stringResource(view.summary.labelResource()),
        style = MaterialTheme.typography.bodySmall
    )
    if (view.lines.isNotEmpty()) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                stringResource(
                    if (expanded) {
                        Res.string.split_explanation_hide_details
                    } else {
                        Res.string.split_explanation_details
                    }
                )
            )
        }
    }
    if (expanded) {
        view.lines.forEach { line ->
            Text(
                text = explanationLineText(line, weightUnit),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** The label alone for a qualitative fact, or "label: value" when the fact recorded a number. */
@Composable
private fun explanationLineText(line: TargetExplanationLine, weightUnit: WeightUnit): String {
    val label = stringResource(line.reason.labelResource())
    val value = line.value
    if (value == null) return label
    val rendered = when (line.valueKind) {
        TargetExplanationValueKind.KILOGRAMS -> stringResource(
            Res.string.split_explanation_kilograms,
            formatWeight(weightUnit.kilogramsToDisplay(value)),
            weightUnit.label
        )
        TargetExplanationValueKind.PERCENT -> formatWeight(value * 100.0) + "%"
        else -> formatWeight(value)
    }
    return stringResource(Res.string.split_explanation_line, label, rendered)
}

private fun TargetExplanationReason.labelResource(): StringResource = when (this) {
    TargetExplanationReason.SUMMARY_AI_PROPOSAL -> Res.string.split_explanation_summary_ai
    TargetExplanationReason.SUMMARY_CALCULATED -> Res.string.split_explanation_summary_calculated
    TargetExplanationReason.SUMMARY_SETS_ONLY -> Res.string.split_explanation_summary_sets_only
    TargetExplanationReason.SUMMARY_UNAVAILABLE_NOT_LOAD_TRACKED ->
        Res.string.split_explanation_unavailable_not_load_tracked
    TargetExplanationReason.SUMMARY_UNAVAILABLE_INSUFFICIENT_EVIDENCE ->
        Res.string.split_explanation_unavailable_insufficient_evidence
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_NOT_SHARED ->
        Res.string.split_explanation_unavailable_ai_not_shared
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_OMITTED ->
        Res.string.split_explanation_unavailable_ai_omitted
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_REJECTED ->
        Res.string.split_explanation_unavailable_ai_rejected
    TargetExplanationReason.BASELINE_RECENT_ESTIMATE ->
        Res.string.split_explanation_baseline_recent_estimate
    TargetExplanationReason.BASELINE_MANUAL_FLOOR ->
        Res.string.split_explanation_baseline_manual_floor
    TargetExplanationReason.BASELINE_QUALIFYING_SETS ->
        Res.string.split_explanation_baseline_qualifying_sets
    TargetExplanationReason.BASELINE_EFFECTIVE -> Res.string.split_explanation_baseline_effective
    TargetExplanationReason.PROGRESSION_APPLIED -> Res.string.split_explanation_progression_applied
    TargetExplanationReason.PROGRESSION_DELTA -> Res.string.split_explanation_progression_delta
    TargetExplanationReason.PROGRESSION_SUCCESS_STREAK_SIZE ->
        Res.string.split_explanation_progression_success_streak_size
    TargetExplanationReason.PROGRESSION_FAILURE_STREAK_SIZE ->
        Res.string.split_explanation_progression_failure_streak_size
    TargetExplanationReason.PROGRESSION_NONE_NO_STREAK ->
        Res.string.split_explanation_progression_none_no_streak
    TargetExplanationReason.PROGRESSION_HELD_AFTER_MISSES ->
        Res.string.split_explanation_progression_held_after_misses
    TargetExplanationReason.PROGRESSION_NONE_NO_PRESCRIPTION ->
        Res.string.split_explanation_progression_none_no_prescription
    TargetExplanationReason.PROGRESSION_PAUSED_DELOAD ->
        Res.string.split_explanation_progression_paused_deload
    TargetExplanationReason.CONVERSION_REPS -> Res.string.split_explanation_conversion_reps
    TargetExplanationReason.CONVERSION_DELOAD_SCALE ->
        Res.string.split_explanation_conversion_deload_scale
    TargetExplanationReason.CONVERSION_INTENSITY ->
        Res.string.split_explanation_conversion_intensity
    TargetExplanationReason.CONVERSION_ROUNDED_TO ->
        Res.string.split_explanation_conversion_rounded_to
    TargetExplanationReason.CONVERSION_BEFORE_CEILING ->
        Res.string.split_explanation_conversion_before_ceiling
    TargetExplanationReason.CONVERSION_CEILING -> Res.string.split_explanation_conversion_ceiling
    TargetExplanationReason.CONVERSION_CEILING_BINDS ->
        Res.string.split_explanation_conversion_ceiling_binds
    TargetExplanationReason.AI_PROPOSED -> Res.string.split_explanation_ai_proposed
    TargetExplanationReason.AI_ACCEPTED -> Res.string.split_explanation_ai_accepted
    TargetExplanationReason.AI_NOT_ACCEPTED -> Res.string.split_explanation_ai_not_accepted
    TargetExplanationReason.AI_BOUNDED_BY_PROGRESSION ->
        Res.string.split_explanation_ai_bounded_by_progression
    TargetExplanationReason.AI_CAPPED_BY_EQUIPMENT ->
        Res.string.split_explanation_ai_capped_by_equipment
    TargetExplanationReason.AI_EQUIPMENT_CEILING ->
        Res.string.split_explanation_ai_equipment_ceiling
    TargetExplanationReason.AI_CEILING_NOT_BINDING ->
        Res.string.split_explanation_ai_ceiling_not_binding
    TargetExplanationReason.AI_DELOAD_SCALE -> Res.string.split_explanation_ai_deload_scale
    TargetExplanationReason.AI_DELOAD_SCALED -> Res.string.split_explanation_ai_deload_scaled
    TargetExplanationReason.SETS_BASE -> Res.string.split_explanation_sets_base
    TargetExplanationReason.SETS_DELOAD_SCALE -> Res.string.split_explanation_sets_deload_scale
    TargetExplanationReason.SETS_REDUCED_FOR_FATIGUE ->
        Res.string.split_explanation_sets_reduced_for_fatigue
    TargetExplanationReason.SETS_FINAL -> Res.string.split_explanation_sets_final
}

@Composable
private fun VolumeExplanationCard(
    coverage: List<ArmMuscleCoverage>,
    attribution: PlanAttribution,
    invalidated: Boolean
) {
    if (invalidated) {
        Text(
            text = stringResource(Res.string.split_volume_substituted),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    if (coverage.isEmpty()) return
    Text(
        text = stringResource(Res.string.split_volume_explanation_title),
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        text = stringResource(
            when (attribution) {
                PlanAttribution.AI_GENERATED -> Res.string.split_volume_ai
                PlanAttribution.DETERMINISTIC -> Res.string.split_volume_deterministic
            }
        ),
        style = MaterialTheme.typography.bodySmall
    )
    coverage.forEach { entry ->
        val name = when (entry.muscle) {
            MuscleGroup.BICEPS -> stringResource(Res.string.split_muscle_biceps)
            MuscleGroup.TRICEPS -> stringResource(Res.string.split_muscle_triceps)
            else -> entry.muscle.name
        }
        Text(
            text = stringResource(
                Res.string.split_volume_direct_label,
                name,
                entry.directIsolationSets,
                entry.targetSets
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                Res.string.split_volume_indirect_label,
                formatVolumeCredits(entry.estimatedOtherInvolvementCredits)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (!entry.isTargetEnforced) {
            Text(
                text = stringResource(Res.string.split_volume_not_enforced),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            entry.unmetReason?.let { reason ->
                Text(
                    text = stringResource(reason.labelResource()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatVolumeCredits(value: Double): String =
    ((value * 10.0).roundToInt() / 10.0).toString()

@Composable
private fun PlanExplanationContext(explanation: PlanExplanation) {
    Text(
        text = stringResource(
            when (explanation.attribution) {
                PlanAttribution.DETERMINISTIC ->
                    Res.string.split_explanation_plan_attribution_deterministic
                PlanAttribution.AI_GENERATED -> Res.string.split_explanation_plan_attribution_ai
            }
        ),
        style = MaterialTheme.typography.bodySmall
    )
    if (explanation.isDeload) {
        Text(
            text = stringResource(
                Res.string.split_explanation_plan_deload_scales,
                formatWeight(explanation.deloadVolumeScale * 100.0) + "%",
                formatWeight(explanation.deloadIntensityScale * 100.0) + "%"
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun ArmCoverageUnmetReason.labelResource(): StringResource = when (this) {
    ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE ->
        Res.string.split_volume_unmet_candidate
    ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE ->
        Res.string.split_volume_unmet_fatigue
    ArmCoverageUnmetReason.COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD ->
        Res.string.split_volume_soreness_constraint
    ArmCoverageUnmetReason.NOT_MET_WITH_AVAILABLE_CANDIDATES ->
        Res.string.split_volume_unmet_available
}

/** A specific message for a mapped failure; null falls back to the generic transient/error text. */
private fun PlanFailureReason?.reasonMessage(): StringResource? = when (this) {
    PlanFailureReason.RATE_LIMITED -> Res.string.split_error_rate_limited
    PlanFailureReason.QUOTA_EXHAUSTED -> Res.string.split_error_quota_exhausted
    PlanFailureReason.SERVICE_UNAVAILABLE -> Res.string.split_error_service_unavailable
    PlanFailureReason.TIMEOUT -> Res.string.split_error_timeout
    PlanFailureReason.NETWORK -> Res.string.split_error_network
    PlanFailureReason.INVALID_API_KEY -> Res.string.split_error_api_key
    PlanFailureReason.INVALID_REQUEST -> Res.string.split_error_invalid_request
    PlanFailureReason.INVALID_RESPONSE -> Res.string.split_error_invalid_response
    PlanFailureReason.NO_ELIGIBLE_EXERCISES -> Res.string.split_error_no_eligible_exercises
    PlanFailureReason.NO_USABLE_EXERCISES -> Res.string.split_error_no_usable_exercises
    PlanFailureReason.UNKNOWN, null -> null
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SplitScheduleDialog(
    dialog: SplitScheduleDialogState,
    viewModel: SplitBuilderViewModel
) {
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::onScheduleDismissed,
        title = { Text(stringResource(Res.string.split_schedule_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = dialog.mode == ScheduleMode.WEEKDAY,
                        onClick = { viewModel.onScheduleModeChanged(ScheduleMode.WEEKDAY) },
                        label = { Text(stringResource(Res.string.split_schedule_mode_weekday)) }
                    )
                    FilterChip(
                        selected = dialog.mode == ScheduleMode.SEQUENCE,
                        onClick = { viewModel.onScheduleModeChanged(ScheduleMode.SEQUENCE) },
                        label = { Text(stringResource(Res.string.split_schedule_mode_sequence)) }
                    )
                }
                if (dialog.mode == ScheduleMode.WEEKDAY) {
                    Text(stringResource(Res.string.split_schedule_weekdays))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in dialog.weekdays,
                                onClick = { viewModel.onScheduleWeekdayToggled(day) },
                                label = { Text(weekdayLabel(day)) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(Res.string.split_schedule_start_today))
                        Switch(
                            checked = dialog.startToday,
                            onCheckedChange = viewModel::onScheduleStartTodayChanged
                        )
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(stringResource(Res.string.split_schedule_choose_date))
                        }
                    }
                    Text(
                        text = isoDateUtc(dialog.startEpochDay * MILLIS_PER_DAY),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(Res.string.split_schedule_preview),
                        style = MaterialTheme.typography.labelMedium
                    )
                    dialog.preview.forEach { day ->
                        Text(
                            text = day?.let { isoDateUtc(it * MILLIS_PER_DAY) }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (dialog.hasActiveBlock) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(Res.string.split_schedule_replace))
                        Switch(
                            checked = dialog.replaceActive,
                            onCheckedChange = viewModel::onScheduleReplaceActiveChanged
                        )
                    }
                }
                dialog.error?.let { error ->
                    Text(
                        text = when (error) {
                            "frequency" -> stringResource(Res.string.split_schedule_frequency)
                            "weekdays" -> stringResource(Res.string.split_schedule_no_weekday)
                            "dates" -> stringResource(Res.string.split_schedule_dates_stale)
                            else -> error
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onConfirmSchedule) {
                Text(stringResource(Res.string.split_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onScheduleDismissed) {
                Text(stringResource(Res.string.split_cancel))
            }
        }
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dialog.startEpochDay * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onScheduleStartDateChosen(millis / MILLIS_PER_DAY)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.split_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.split_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanningDatesDialog(dialog: PlanningDatesState, viewModel: SplitBuilderViewModel) {
    var showDatePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::onPlanningDatesDialogDismissed,
        title = { Text(stringResource(Res.string.split_planning_dates_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.split_planning_dates_disclosure),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(Res.string.split_planning_dates_clock),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(Res.string.split_planning_dates_unknown_note),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(Res.string.split_planning_dates_not_block),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(stringResource(Res.string.split_schedule_weekdays))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in dialog.weekdays,
                            onClick = { viewModel.onPlanningDatesWeekdayToggled(day) },
                            label = { Text(weekdayLabel(day)) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(Res.string.split_schedule_start_today))
                    Switch(
                        checked = dialog.startToday,
                        onCheckedChange = viewModel::onPlanningDatesStartTodayChanged
                    )
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(Res.string.split_schedule_choose_date))
                    }
                }
                Text(
                    text = isoDateUtc(dialog.startEpochDay * MILLIS_PER_DAY),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::onPlanningDatesShiftedOneWeek) {
                        Text(stringResource(Res.string.split_planning_dates_shift))
                    }
                    TextButton(onClick = viewModel::onPlanningDatesCleared) {
                        Text(stringResource(Res.string.split_planning_dates_clear))
                    }
                }
                Text(
                    text = stringResource(Res.string.split_planning_dates_preview),
                    style = MaterialTheme.typography.labelMedium
                )
                dialog.preview.forEach { day ->
                    Text(
                        text = day?.let { isoDateUtc(it * MILLIS_PER_DAY) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                dialog.error?.let { error ->
                    Text(
                        text = when (error) {
                            "frequency" -> stringResource(Res.string.split_schedule_frequency)
                            "past" -> stringResource(Res.string.split_planning_dates_past)
                            else -> error
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onPlanningDatesConfirmed) {
                Text(stringResource(Res.string.split_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onPlanningDatesDialogDismissed) {
                Text(stringResource(Res.string.split_cancel))
            }
        }
    )
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dialog.startEpochDay * MILLIS_PER_DAY
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onPlanningDatesStartDateChosen(millis / MILLIS_PER_DAY)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.split_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.split_cancel))
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun weekdayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> stringResource(Res.string.weekday_monday)
    DayOfWeek.TUESDAY -> stringResource(Res.string.weekday_tuesday)
    DayOfWeek.WEDNESDAY -> stringResource(Res.string.weekday_wednesday)
    DayOfWeek.THURSDAY -> stringResource(Res.string.weekday_thursday)
    DayOfWeek.FRIDAY -> stringResource(Res.string.weekday_friday)
    DayOfWeek.SATURDAY -> stringResource(Res.string.weekday_saturday)
    DayOfWeek.SUNDAY -> stringResource(Res.string.weekday_sunday)
}

/** A compact line describing the applied automatic per-set opt-in. */
@Composable
private fun automaticSetPrescriptionSummary(applied: AutomaticSetPrescription): String =
    stringResource(
        Res.string.split_auto_targets_summary,
        automaticShapeLabel(applied.shape),
        applied.minReps,
        applied.maxReps
    )

@Composable
private fun automaticShapeLabel(shape: SetPrescriptionShape): String = when (shape) {
    SetPrescriptionShape.STRAIGHT -> stringResource(Res.string.split_auto_targets_shape_straight)
    SetPrescriptionShape.ASCENDING_LOAD ->
        stringResource(Res.string.split_auto_targets_shape_ascending_load)
    SetPrescriptionShape.DESCENDING_LOAD ->
        stringResource(Res.string.split_auto_targets_shape_descending_load)
    SetPrescriptionShape.PYRAMID -> stringResource(Res.string.split_auto_targets_shape_pyramid)
}

@Composable
private fun automaticSetPrescriptionErrorText(error: AutomaticSetPrescriptionError): String =
    when (error) {
        AutomaticSetPrescriptionError.REPS_RANGE -> stringResource(
            Res.string.split_auto_targets_error_reps,
            PrescriptionBounds.MIN_REPS,
            PrescriptionBounds.MAX_REPS
        )
        AutomaticSetPrescriptionError.RIR_RANGE -> stringResource(
            Res.string.split_auto_targets_error_rir,
            SetTarget.MIN_RIR,
            SetTarget.MAX_RIR
        )
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutomaticSetPrescriptionDialog(
    dialog: AutomaticSetPrescriptionState,
    viewModel: SplitBuilderViewModel
) {
    AlertDialog(
        onDismissRequest = viewModel::onAutomaticSetPrescriptionDismissed,
        title = { Text(stringResource(Res.string.split_auto_targets_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.split_auto_targets_shape_label),
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SetPrescriptionShape.entries.forEach { shape ->
                        FilterChip(
                            selected = dialog.shape == shape,
                            onClick = { viewModel.onAutomaticSetPrescriptionShapeChanged(shape) },
                            label = { Text(automaticShapeLabel(shape)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = dialog.minReps,
                    onValueChange = viewModel::onAutomaticSetPrescriptionMinRepsChanged,
                    label = { Text(stringResource(Res.string.split_auto_targets_min_reps_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dialog.maxReps,
                    onValueChange = viewModel::onAutomaticSetPrescriptionMaxRepsChanged,
                    label = { Text(stringResource(Res.string.split_auto_targets_max_reps_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dialog.targetRir,
                    onValueChange = viewModel::onAutomaticSetPrescriptionRirChanged,
                    label = { Text(stringResource(Res.string.split_auto_targets_rir_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                dialog.error?.let { error ->
                    Text(
                        text = automaticSetPrescriptionErrorText(error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::onAutomaticSetPrescriptionApply) {
                Text(stringResource(Res.string.split_auto_targets_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::onAutomaticSetPrescriptionDismissed) {
                Text(stringResource(Res.string.split_cancel))
            }
        }
    )
}
