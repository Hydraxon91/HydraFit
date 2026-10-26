package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.domain.workout.GuidedExerciseProgress
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.exercisecontent.ExerciseInstructionDialog
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalProgressNavigator
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.focus_full_body
import hydrafit.feature.logger.generated.resources.focus_legs
import hydrafit.feature.logger.generated.resources.focus_lower
import hydrafit.feature.logger.generated.resources.focus_pull
import hydrafit.feature.logger.generated.resources.focus_push
import hydrafit.feature.logger.generated.resources.focus_upper
import hydrafit.feature.logger.generated.resources.logger_active_progress
import hydrafit.feature.logger.generated.resources.logger_active_workout
import hydrafit.feature.logger.generated.resources.logger_add_weight
import hydrafit.feature.logger.generated.resources.logger_backdated_at
import hydrafit.feature.logger.generated.resources.logger_cancel
import hydrafit.feature.logger.generated.resources.logger_confirm
import hydrafit.feature.logger.generated.resources.logger_confirm_all
import hydrafit.feature.logger.generated.resources.logger_delete_set
import hydrafit.feature.logger.generated.resources.logger_dismiss
import hydrafit.feature.logger.generated.resources.logger_draft_edit
import hydrafit.feature.logger.generated.resources.logger_draft_edit_apply
import hydrafit.feature.logger.generated.resources.logger_draft_edit_title
import hydrafit.feature.logger.generated.resources.logger_draft_load_enter
import hydrafit.feature.logger.generated.resources.logger_draft_load_prompt_body
import hydrafit.feature.logger.generated.resources.logger_draft_load_prompt_title
import hydrafit.feature.logger.generated.resources.logger_draft_load_use_last
import hydrafit.feature.logger.generated.resources.logger_draft_load_without
import hydrafit.feature.logger.generated.resources.logger_draft_more_options
import hydrafit.feature.logger.generated.resources.logger_draft_prescribed_effort
import hydrafit.feature.logger.generated.resources.logger_draft_prescribed_effort_varies
import hydrafit.feature.logger.generated.resources.logger_draft_reset
import hydrafit.feature.logger.generated.resources.logger_draft_set_target
import hydrafit.feature.logger.generated.resources.logger_draft_set_target_rir
import hydrafit.feature.logger.generated.resources.logger_draft_set_targets_summary
import hydrafit.feature.logger.generated.resources.logger_draft_timed_line
import hydrafit.feature.logger.generated.resources.logger_draft_write_none
import hydrafit.feature.logger.generated.resources.logger_draft_write_partial
import hydrafit.feature.logger.generated.resources.logger_edit_set
import hydrafit.feature.logger.generated.resources.logger_end_session
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_accepted
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_bounded_by_progression
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_capped_by_equipment
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_ceiling_not_binding
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_deload_scale
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_deload_scaled
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_equipment_ceiling
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_not_accepted
import hydrafit.feature.logger.generated.resources.logger_explanation_ai_proposed
import hydrafit.feature.logger.generated.resources.logger_explanation_baseline_effective
import hydrafit.feature.logger.generated.resources.logger_explanation_baseline_manual_floor
import hydrafit.feature.logger.generated.resources.logger_explanation_baseline_qualifying_sets
import hydrafit.feature.logger.generated.resources.logger_explanation_baseline_recent_estimate
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_before_ceiling
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_ceiling
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_ceiling_binds
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_deload_scale
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_intensity
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_reps
import hydrafit.feature.logger.generated.resources.logger_explanation_conversion_rounded_to
import hydrafit.feature.logger.generated.resources.logger_explanation_details
import hydrafit.feature.logger.generated.resources.logger_explanation_hide_details
import hydrafit.feature.logger.generated.resources.logger_explanation_kilograms
import hydrafit.feature.logger.generated.resources.logger_explanation_line
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_applied
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_delta
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_failure_streak_size
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_held_after_misses
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_none_no_prescription
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_none_no_streak
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_paused_deload
import hydrafit.feature.logger.generated.resources.logger_explanation_progression_success_streak_size
import hydrafit.feature.logger.generated.resources.logger_explanation_sets_base
import hydrafit.feature.logger.generated.resources.logger_explanation_sets_deload_scale
import hydrafit.feature.logger.generated.resources.logger_explanation_sets_final
import hydrafit.feature.logger.generated.resources.logger_explanation_sets_reduced_for_fatigue
import hydrafit.feature.logger.generated.resources.logger_explanation_summary_ai
import hydrafit.feature.logger.generated.resources.logger_explanation_summary_calculated
import hydrafit.feature.logger.generated.resources.logger_explanation_summary_sets_only
import hydrafit.feature.logger.generated.resources.logger_explanation_unavailable_ai_not_shared
import hydrafit.feature.logger.generated.resources.logger_explanation_unavailable_ai_omitted
import hydrafit.feature.logger.generated.resources.logger_explanation_unavailable_ai_rejected
import hydrafit.feature.logger.generated.resources.logger_explanation_unavailable_insufficient_evidence
import hydrafit.feature.logger.generated.resources.logger_explanation_unavailable_not_load_tracked
import hydrafit.feature.logger.generated.resources.logger_finish
import hydrafit.feature.logger.generated.resources.logger_finish_partial
import hydrafit.feature.logger.generated.resources.logger_future_time_error
import hydrafit.feature.logger.generated.resources.logger_guided_all_done
import hydrafit.feature.logger.generated.resources.logger_guided_collapse_sets
import hydrafit.feature.logger.generated.resources.logger_guided_expand_sets
import hydrafit.feature.logger.generated.resources.logger_guided_log_completed_now
import hydrafit.feature.logger.generated.resources.logger_guided_set_failed
import hydrafit.feature.logger.generated.resources.logger_guided_set_started
import hydrafit.feature.logger.generated.resources.logger_guided_start_set
import hydrafit.feature.logger.generated.resources.logger_guided_target
import hydrafit.feature.logger.generated.resources.logger_guided_target_timed
import hydrafit.feature.logger.generated.resources.logger_guided_title
import hydrafit.feature.logger.generated.resources.logger_history_open
import hydrafit.feature.logger.generated.resources.logger_history_set_duration_line
import hydrafit.feature.logger.generated.resources.logger_history_set_line
import hydrafit.feature.logger.generated.resources.logger_instructions
import hydrafit.feature.logger.generated.resources.logger_load_added
import hydrafit.feature.logger.generated.resources.logger_load_added_none
import hydrafit.feature.logger.generated.resources.logger_load_basis
import hydrafit.feature.logger.generated.resources.logger_load_basis_per_dumbbell
import hydrafit.feature.logger.generated.resources.logger_load_basis_per_hand
import hydrafit.feature.logger.generated.resources.logger_load_bodyweight
import hydrafit.feature.logger.generated.resources.logger_load_confirm_body
import hydrafit.feature.logger.generated.resources.logger_load_confirm_title
import hydrafit.feature.logger.generated.resources.logger_load_legacy
import hydrafit.feature.logger.generated.resources.logger_load_use_bodyweight
import hydrafit.feature.logger.generated.resources.logger_load_use_external
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_action
import hydrafit.feature.logger.generated.resources.logger_loading_calculator_open
import hydrafit.feature.logger.generated.resources.logger_log_button
import hydrafit.feature.logger.generated.resources.logger_log_failed
import hydrafit.feature.logger.generated.resources.logger_log_refresh_failed
import hydrafit.feature.logger.generated.resources.logger_new_session
import hydrafit.feature.logger.generated.resources.logger_no_load_history
import hydrafit.feature.logger.generated.resources.logger_no_load_inventory
import hydrafit.feature.logger.generated.resources.logger_note_clear
import hydrafit.feature.logger.generated.resources.logger_note_failed
import hydrafit.feature.logger.generated.resources.logger_note_label
import hydrafit.feature.logger.generated.resources.logger_note_load_failed
import hydrafit.feature.logger.generated.resources.logger_note_retry
import hydrafit.feature.logger.generated.resources.logger_note_save
import hydrafit.feature.logger.generated.resources.logger_note_saved
import hydrafit.feature.logger.generated.resources.logger_per_hand
import hydrafit.feature.logger.generated.resources.logger_pick_time_title
import hydrafit.feature.logger.generated.resources.logger_planned_today
import hydrafit.feature.logger.generated.resources.logger_progress_open
import hydrafit.feature.logger.generated.resources.logger_recent
import hydrafit.feature.logger.generated.resources.logger_replace
import hydrafit.feature.logger.generated.resources.logger_reps_label
import hydrafit.feature.logger.generated.resources.logger_rest_timer_cancel
import hydrafit.feature.logger.generated.resources.logger_rest_timer_duration
import hydrafit.feature.logger.generated.resources.logger_rest_timer_finished
import hydrafit.feature.logger.generated.resources.logger_rest_timer_note
import hydrafit.feature.logger.generated.resources.logger_rest_timer_pair_duration
import hydrafit.feature.logger.generated.resources.logger_rest_timer_pair_note
import hydrafit.feature.logger.generated.resources.logger_rest_timer_remaining
import hydrafit.feature.logger.generated.resources.logger_rest_timer_transition_duration
import hydrafit.feature.logger.generated.resources.logger_rest_timer_transition_note
import hydrafit.feature.logger.generated.resources.logger_rest_timer_use_default
import hydrafit.feature.logger.generated.resources.logger_search_label
import hydrafit.feature.logger.generated.resources.logger_seconds_label
import hydrafit.feature.logger.generated.resources.logger_session_active
import hydrafit.feature.logger.generated.resources.logger_session_none
import hydrafit.feature.logger.generated.resources.logger_session_not_recorded
import hydrafit.feature.logger.generated.resources.logger_session_note_failed
import hydrafit.feature.logger.generated.resources.logger_session_note_label
import hydrafit.feature.logger.generated.resources.logger_session_note_load_failed
import hydrafit.feature.logger.generated.resources.logger_session_note_loading
import hydrafit.feature.logger.generated.resources.logger_session_note_none
import hydrafit.feature.logger.generated.resources.logger_session_note_open
import hydrafit.feature.logger.generated.resources.logger_session_note_saved
import hydrafit.feature.logger.generated.resources.logger_session_recorded
import hydrafit.feature.logger.generated.resources.logger_set_time
import hydrafit.feature.logger.generated.resources.logger_set_week_day
import hydrafit.feature.logger.generated.resources.logger_short_rest_coaching
import hydrafit.feature.logger.generated.resources.logger_short_rest_dismiss
import hydrafit.feature.logger.generated.resources.logger_skip
import hydrafit.feature.logger.generated.resources.logger_station_attributed
import hydrafit.feature.logger.generated.resources.logger_station_choose
import hydrafit.feature.logger.generated.resources.logger_station_none
import hydrafit.feature.logger.generated.resources.logger_superset_next
import hydrafit.feature.logger.generated.resources.logger_superset_pair_note
import hydrafit.feature.logger.generated.resources.logger_superset_resume_turn
import hydrafit.feature.logger.generated.resources.logger_superset_skip_turn
import hydrafit.feature.logger.generated.resources.logger_superset_unpair
import hydrafit.feature.logger.generated.resources.logger_target_current_session
import hydrafit.feature.logger.generated.resources.logger_target_label
import hydrafit.feature.logger.generated.resources.logger_technique_considerations
import hydrafit.feature.logger.generated.resources.logger_time_live
import hydrafit.feature.logger.generated.resources.logger_title
import hydrafit.feature.logger.generated.resources.logger_today
import hydrafit.feature.logger.generated.resources.logger_use_now
import hydrafit.feature.logger.generated.resources.logger_warmup
import hydrafit.feature.logger.generated.resources.logger_warmup_action_easy_general
import hydrafit.feature.logger.generated.resources.logger_warmup_action_light_pull
import hydrafit.feature.logger.generated.resources.logger_warmup_action_light_push
import hydrafit.feature.logger.generated.resources.logger_warmup_action_lower_body
import hydrafit.feature.logger.generated.resources.logger_warmup_action_shoulder_circles
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_edit
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_edited_suffix
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_editor_minutes
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_editor_reset
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_editor_title
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_editor_weight_exceeds
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_hide
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_item_reps
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_item_timed
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_lock_note
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_log_ramp
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_note
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_preparation
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_ramp_failed
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_ramp_line
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_ramp_line_edited
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_ramp_line_limited
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_ramps_title
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_restore_skipped
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_show
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_skip
import hydrafit.feature.logger.generated.resources.logger_warmup_guidance_title
import hydrafit.feature.logger.generated.resources.logger_warmup_suffix
import hydrafit.feature.logger.generated.resources.logger_weight_label
import hydrafit.feature.logger.generated.resources.logger_weight_none
import hydrafit.feature.logger.generated.resources.nav_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val loggerRoute: String = "log"

/** The Logger's own screen; the tab route [loggerRoute] is its parent graph. */
private val loggerHomeRoute: String = "log/home"

val loggerDestination: FeatureDestination = FeatureDestination(
    route = loggerRoute,
    label = Res.string.nav_label,
    graph = { navController -> { loggerGraph(navController) } }
)

/**
 * A real nested graph keeps the Log tab selected on the history sub-screens, which the shell matches
 * by destination hierarchy.
 */
fun NavGraphBuilder.loggerGraph(navController: NavController) {
    navigation(startDestination = loggerHomeRoute, route = loggerRoute) {
        composable(loggerHomeRoute) {
            val viewModel = sharedLoggerViewModel(navController) ?: return@composable
            val openProgress = LocalProgressNavigator.current
            WorkoutLoggerRoute(
                viewModel = viewModel,
                onOpenHistory = { navController.navigate(historyGraphRoute) },
                onOpenProgress = openProgress,
                onOpenSummary = { navController.navigate(summaryRoute) }
            )
        }
        composable(summaryRoute) {
            val viewModel = sharedLoggerViewModel(navController) ?: return@composable
            val state by viewModel.state.collectAsStateWithLifecycle()
            // The records section is route-owned: a new summary occurrence re-points it, and popping
            // the route disposes the ViewModel with its observation.
            val recordsViewModel: WorkoutSummaryRecordsViewModel = koinViewModel()
            val recordsState by recordsViewModel.state.collectAsStateWithLifecycle()
            val summaryOccurrenceId =
                (state.summary as? WorkoutSummaryScreen.Ready)?.summary?.occurrenceId
            LaunchedEffect(summaryOccurrenceId) {
                recordsViewModel.onOccurrenceChanged(summaryOccurrenceId)
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recordsViewModel.onResume() }
            // The route exists only while a summary is open, so it must clear the state that opened
            // it whenever it leaves (Done, system Back or the history handoff). Without this, Back
            // pops the route while the state stays open and the Logger immediately reopens it.
            DisposableEffect(Unit) { onDispose { viewModel.onWorkoutSummaryShown() } }
            WorkoutSummaryRoute(
                screen = state.summary,
                records = recordsState,
                weightUnit = state.weightUnit,
                utcOffsetMillis = state.utcOffsetMillis,
                onRetry = viewModel::retryWorkoutSummary,
                onRecordsRetry = recordsViewModel::onRetry,
                onDone = {
                    viewModel.onWorkoutSummaryShown()
                    navController.popBackStack()
                },
                onViewInHistory = { summary ->
                    // Leave the transient summary before entering the browser, so returning from
                    // history lands on the Logger instead of a dismissed summary.
                    navController.popBackStack()
                    navController.navigate(historyGraphRoute)
                    viewModel.onWorkoutSummaryHistoryRequested(summary)
                },
                onEditSet = viewModel::editSummarySet
            )
            // The same correction editor the Logger's recent sets use; it is graph-scoped state, so
            // it has to be rendered on this route too.
            state.loggedSetEdit?.let { edit ->
                LoggedSetEditDialog(
                    edit = edit,
                    saving = state.savingLoggedSet,
                    failed = state.loggedSetEditFailed,
                    utcOffsetMillis = state.utcOffsetMillis,
                    nowMillis = viewModel::currentTimeMillis,
                    onRepsChanged = viewModel::onLoggedSetRepsChanged,
                    onDurationChanged = viewModel::onLoggedSetDurationChanged,
                    onMetricChanged = viewModel::onLoggedSetMetricChanged,
                    onWeightChanged = viewModel::onLoggedSetWeightChanged,
                    onRirChanged = viewModel::onLoggedSetRirChanged,
                    onTimeChanged = viewModel::onLoggedSetTimeChanged,
                    onCancel = viewModel::cancelLoggedSetEdit,
                    onSave = viewModel::saveLoggedSetEdit
                )
            }
        }
        historyDestinations(navController)
    }
}

@Composable
fun WorkoutLoggerRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkoutLoggerViewModel,
    replacementViewModel: OccurrenceReplacementViewModel = koinViewModel(),
    loadingCalculatorViewModel: LoggerLoadingCalculatorViewModel = koinViewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenSummary: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val replacementState by replacementViewModel.state.collectAsStateWithLifecycle()
    val loadingCalculator by loadingCalculatorViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    // The helper's own Logger context: a moved exercise/session, occurrence or draft/guided source
    // closes it instead of reinterpreting the target it captured.
    val loadingSourceIds = buildSet {
        state.draftSets.forEach { add(it.occurrenceEntryId?.toString() ?: it.exerciseId) }
        state.guidedProgress?.exercises?.forEach { add(it.occurrenceEntryId.toString()) }
    }
    LaunchedEffect(
        state.selectedExerciseId,
        state.activeSession?.id,
        state.activeOccurrence?.occurrenceId,
        loadingSourceIds
    ) {
        loadingCalculatorViewModel.onSourceContextChanged(
            LoggerLoadingSourceContext(
                selectedExerciseId = state.selectedExerciseId,
                sessionId = state.activeSession?.id,
                occurrenceId = state.activeOccurrence?.occurrenceId,
                availableSourceIds = loadingSourceIds
            )
        )
    }
    // A finished workout opens its summary once; the route keeps the Log tab selected.
    val summaryOpen = state.summary != null
    LaunchedEffect(summaryOpen) { if (summaryOpen) onOpenSummary() }
    WorkoutLoggerScreen(
        state = state,
        onExerciseSelected = viewModel::onExerciseSelected,
        onManualInstructions = viewModel::openManualInstructions,
        onDraftInstructions = viewModel::openDraftInstructions,
        onGuidedInstructions = viewModel::openGuidedInstructions,
        onDismissInstructions = viewModel::dismissInstructions,
        onExerciseSearchChanged = viewModel::onExerciseSearchChanged,
        onRepsChanged = viewModel::onRepsChanged,
        onDurationSecondsChanged = viewModel::onDurationSecondsChanged,
        onMetricChanged = viewModel::onMetricChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onStationSelected = viewModel::onStationSelected,
        onRirChanged = viewModel::onRirChanged,
        onWarmupToggled = viewModel::onWarmupToggled,
        onLog = viewModel::log,
        onDeleteSet = viewModel::deleteSet,
        onRecentSetSelected = viewModel::onRecentSetSelected,
        onEditLoggedSet = viewModel::editLoggedSet,
        onLoggedSetRepsChanged = viewModel::onLoggedSetRepsChanged,
        onLoggedSetDurationChanged = viewModel::onLoggedSetDurationChanged,
        onLoggedSetMetricChanged = viewModel::onLoggedSetMetricChanged,
        onLoggedSetWeightChanged = viewModel::onLoggedSetWeightChanged,
        onLoggedSetRirChanged = viewModel::onLoggedSetRirChanged,
        onLoggedSetTimeChanged = viewModel::onLoggedSetTimeChanged,
        onCancelLoggedSetEdit = viewModel::cancelLoggedSetEdit,
        onSaveLoggedSetEdit = viewModel::saveLoggedSetEdit,
        onRevealWeight = viewModel::onRevealWeight,
        onConfirmDraft = viewModel::confirmDraft,
        onEditDraft = viewModel::editDraft,
        onDraftRepsChanged = viewModel::onDraftRepsChanged,
        onDraftDurationSecondsChanged = viewModel::onDraftDurationSecondsChanged,
        onDraftWeightChanged = viewModel::onDraftWeightChanged,
        onDraftRirChanged = viewModel::onDraftRirChanged,
        onDraftWeightRevealed = viewModel::onDraftWeightRevealed,
        onDraftPerformedAtChanged = viewModel::onDraftPerformedAtChanged,
        onCancelDraftEdit = viewModel::cancelDraftEdit,
        onResetDraftEdit = viewModel::resetDraftEdit,
        onConfirmDraftEdit = viewModel::confirmDraftEdit,
        onCancelMissingLoadPrompt = viewModel::cancelMissingLoadPrompt,
        onUseLastLoggedLoad = viewModel::useLastLoggedLoad,
        onEnterMissingLoad = viewModel::enterMissingLoad,
        onLogMissingLoadWithoutWeight = viewModel::logMissingLoadWithoutWeight,
        onConfirmAllDrafts = viewModel::confirmAllDrafts,
        onDismissDraft = viewModel::dismissDraft,
        onResolveLegacyAsBodyweight = viewModel::resolveLegacyAsBodyweight,
        onResolveLegacyAsExternal = viewModel::resolveLegacyAsExternal,
        onDismissLegacyResolution = viewModel::dismissLegacyResolution,
        onEndSession = viewModel::endSession,
        onNewSession = viewModel::newSession,
        onFinishWorkout = viewModel::finishWorkout,
        onFinishWorkoutPartially = viewModel::finishWorkoutPartially,
        onSkipWorkout = viewModel::skipWorkout,
        onOccurrenceMessageShown = viewModel::onOccurrenceMessageShown,
        onConfirmGuidedSet = viewModel::confirmGuidedSet,
        onConfirmGuidedSetNow = viewModel::confirmGuidedSetNow,
        onStartGuidedSet = viewModel::startGuidedSet,
        onEditGuidedSet = viewModel::editGuidedSet,
        onSkipSupersetTurn = viewModel::skipSupersetTurn,
        onResumeSupersetTurn = viewModel::resumeSupersetTurn,
        onUnpairSuperset = viewModel::unpairSuperset,
        onToggleGuidedExpanded = viewModel::toggleGuidedEntryExpanded,
        nextGuidedEntryId = state.nextGuidedEntryId,
        supersetSkippedEntryId = state.supersetSkippedEntryId,
        onRestDurationChanged = viewModel::onRestDurationChanged,
        onResetRestDuration = viewModel::resetExerciseRestDuration,
        onCancelRestTimer = viewModel::cancelRestTimer,
        onDismissShortRestCoaching = viewModel::dismissShortRestCoaching,
        onWarmupPreparationToggled = viewModel::onWarmupPreparationToggled,
        onToggleWarmupGuidance = viewModel::onToggleWarmupGuidance,
        onLogWarmupRamp = viewModel::logWarmupRamp,
        onEditWarmupPreparation = viewModel::editWarmupPreparation,
        onSkipWarmupPreparation = viewModel::toggleWarmupPreparationSkipped,
        onRestoreSkippedWarmupPreparation = viewModel::restoreSkippedWarmupPreparation,
        onEditWarmupRamp = viewModel::editWarmupRamp,
        onWarmupEditorRepsChanged = viewModel::onWarmupEditorRepsChanged,
        onWarmupEditorDurationChanged = viewModel::onWarmupEditorDurationChanged,
        onWarmupEditorWeightChanged = viewModel::onWarmupEditorWeightChanged,
        onResetWarmupEditor = viewModel::resetWarmupEditor,
        onCancelWarmupEditor = viewModel::cancelWarmupEditor,
        onSaveWarmupEditor = viewModel::saveWarmupEditor,
        onReplaceOccurrenceEntry = { entryId ->
            state.activeOccurrence?.occurrenceId?.let { replacementViewModel.open(it, entryId) }
        },
        onBackdatedDateTimePicked = viewModel::onBackdatedDateTimePicked,
        onClearBackdated = { viewModel.onPerformedAtChanged(null) },
        onForceNewSessionChanged = viewModel::onForceNewSessionChanged,
        onNoteExerciseSelected = viewModel::onNoteExerciseSelected,
        onNoteTextChanged = viewModel::onNoteTextChanged,
        onSaveNote = viewModel::saveNote,
        onClearNote = viewModel::clearNote,
        onCancelNote = viewModel::cancelNote,
        onRetryNoteLoad = viewModel::retryNoteLoad,
        onSessionNoteOpen = viewModel::openSessionNote,
        onSessionNoteTextChanged = viewModel::onSessionNoteTextChanged,
        onSessionNoteSave = viewModel::saveSessionNote,
        onSessionNoteClear = viewModel::clearSessionNote,
        onSessionNoteCancel = viewModel::cancelSessionNote,
        onSessionNoteRetry = viewModel::retrySessionNoteLoad,
        onOpenHistory = onOpenHistory,
        onOpenProgress = onOpenProgress,
        nowMillis = viewModel::currentTimeMillis,
        loadingCalculator = loadingCalculator,
        onManualLoadingCalculator = {
            loadingCalculatorViewModel.openManual(
                selectedExerciseId = state.selectedExerciseId,
                sessionId = state.activeSession?.id
            )
        },
        onDraftLoadingCalculator = { draft ->
            loadingCalculatorViewModel.openTarget(
                loggerLoadingRequest(
                    source = LoggerLoadingTargetSource.DRAFT,
                    sourceId = draft.occurrenceEntryId?.toString() ?: draft.exerciseId,
                    sourceName = draft.name,
                    loadKind = draft.loadKind,
                    weightKg = draft.weightKg,
                    occurrenceId = draft.occurrenceId
                )
            )
        },
        onGuidedLoadingCalculator = { exercise ->
            loadingCalculatorViewModel.openTarget(
                loggerLoadingRequest(
                    source = LoggerLoadingTargetSource.GUIDED,
                    sourceId = exercise.occurrenceEntryId.toString(),
                    sourceName = exercise.exerciseName,
                    loadKind = exercise.loadKind,
                    weightKg = exercise.weightKg,
                    occurrenceId = state.guidedProgress?.occurrenceId
                        ?: state.activeOccurrence?.occurrenceId
                )
            )
        },
        onLoadingCalculatorKindChanged = loadingCalculatorViewModel::onKindChanged,
        onLoadingCalculatorEquipmentSelected = loadingCalculatorViewModel::onEquipmentSelected,
        onLoadingCalculatorTargetChanged = loadingCalculatorViewModel::onTargetChanged,
        onRunLoadingCalculator = loadingCalculatorViewModel::onRun,
        onDismissLoadingCalculator = loadingCalculatorViewModel::dismiss,
        modifier = modifier
    )
    OccurrenceReplacementDialog(
        replacementState,
        state.weightUnit,
        replacementViewModel::confirm,
        replacementViewModel::dismiss
    )
}

/** The helper request one Logger source produces; the source identity stays stable. */
private fun loggerLoadingRequest(
    source: LoggerLoadingTargetSource,
    sourceId: String,
    sourceName: String,
    loadKind: LoadKind,
    weightKg: Double?,
    occurrenceId: Long?
): LoggerLoadingCalculatorRequest = LoggerLoadingCalculatorRequest(
    source = source,
    sourceId = sourceId,
    sourceName = sourceName,
    loadKind = loadKind,
    weightKg = weightKg,
    occurrenceId = occurrenceId
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggerScreen(
    state: WorkoutLoggerUiState,
    onExerciseSelected: (String) -> Unit,
    onManualInstructions: () -> Unit,
    onDraftInstructions: (DraftSet) -> Unit,
    onGuidedInstructions: (Long) -> Unit,
    onDismissInstructions: () -> Unit,
    onExerciseSearchChanged: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onDurationSecondsChanged: (String) -> Unit,
    onMetricChanged: (SetMetric) -> Unit,
    onWeightChanged: (String) -> Unit,
    onStationSelected: (Long?) -> Unit,
    onRirChanged: (String) -> Unit,
    onWarmupToggled: (Boolean) -> Unit,
    onLog: () -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRecentSetSelected: (LoggedSetRow) -> Unit,
    onEditLoggedSet: (LoggedSetRow) -> Unit,
    onLoggedSetRepsChanged: (String) -> Unit,
    onLoggedSetDurationChanged: (String) -> Unit,
    onLoggedSetMetricChanged: (SetMetric) -> Unit,
    onLoggedSetWeightChanged: (String) -> Unit,
    onLoggedSetRirChanged: (String) -> Unit,
    onLoggedSetTimeChanged: (Long) -> Boolean,
    onCancelLoggedSetEdit: () -> Unit,
    onSaveLoggedSetEdit: () -> Unit,
    onRevealWeight: () -> Unit,
    onConfirmDraft: (DraftSet) -> Unit,
    onEditDraft: (DraftSet) -> Unit,
    onDraftRepsChanged: (String) -> Unit,
    onDraftDurationSecondsChanged: (String) -> Unit,
    onDraftWeightChanged: (String) -> Unit,
    onDraftRirChanged: (String) -> Unit,
    onDraftWeightRevealed: () -> Unit,
    onDraftPerformedAtChanged: (Long?) -> Boolean,
    onCancelDraftEdit: () -> Unit,
    onResetDraftEdit: () -> Unit,
    onConfirmDraftEdit: () -> Unit,
    onCancelMissingLoadPrompt: () -> Unit,
    onUseLastLoggedLoad: () -> Unit,
    onEnterMissingLoad: () -> Unit,
    onLogMissingLoadWithoutWeight: () -> Unit,
    onConfirmAllDrafts: () -> Unit,
    onDismissDraft: (DraftSet) -> Unit,
    onResolveLegacyAsBodyweight: () -> Unit,
    onResolveLegacyAsExternal: () -> Unit,
    onDismissLegacyResolution: () -> Unit,
    onEndSession: () -> Unit,
    onNewSession: () -> Unit,
    onFinishWorkout: () -> Unit,
    onFinishWorkoutPartially: () -> Unit,
    onSkipWorkout: () -> Unit,
    onOccurrenceMessageShown: () -> Unit,
    onConfirmGuidedSet: (Long) -> Unit,
    onConfirmGuidedSetNow: (Long) -> Unit,
    onStartGuidedSet: (Long) -> Unit,
    onEditGuidedSet: (Long) -> Unit,
    onSkipSupersetTurn: (Long) -> Unit = {},
    onResumeSupersetTurn: () -> Unit = {},
    onUnpairSuperset: (Long) -> Unit = {},
    onToggleGuidedExpanded: (Long) -> Unit = {},
    nextGuidedEntryId: Long? = null,
    supersetSkippedEntryId: Long? = null,
    onRestDurationChanged: (String) -> Unit,
    onResetRestDuration: () -> Unit,
    onCancelRestTimer: () -> Unit,
    onDismissShortRestCoaching: () -> Unit,
    onWarmupPreparationToggled: (WarmupAction) -> Unit,
    onToggleWarmupGuidance: () -> Unit,
    onLogWarmupRamp: (Long, Int) -> Unit,
    onEditWarmupPreparation: (WarmupAction) -> Unit,
    onSkipWarmupPreparation: (WarmupAction) -> Unit,
    onRestoreSkippedWarmupPreparation: () -> Unit,
    onEditWarmupRamp: (Long, Int) -> Unit,
    onWarmupEditorRepsChanged: (String) -> Unit,
    onWarmupEditorDurationChanged: (String) -> Unit,
    onWarmupEditorWeightChanged: (String) -> Unit,
    onResetWarmupEditor: () -> Unit,
    onCancelWarmupEditor: () -> Unit,
    onSaveWarmupEditor: () -> Unit,
    onReplaceOccurrenceEntry: (Long) -> Unit,
    onBackdatedDateTimePicked: (Long, Int, Int) -> Boolean,
    onClearBackdated: () -> Unit,
    onForceNewSessionChanged: (Boolean) -> Unit,
    onNoteExerciseSelected: (String) -> Unit,
    onNoteTextChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onClearNote: () -> Unit,
    onCancelNote: () -> Unit,
    onRetryNoteLoad: () -> Unit,
    onSessionNoteOpen: () -> Unit,
    onSessionNoteTextChanged: (String) -> Unit,
    onSessionNoteSave: () -> Unit,
    onSessionNoteClear: () -> Unit,
    onSessionNoteCancel: () -> Unit,
    onSessionNoteRetry: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProgress: () -> Unit,
    nowMillis: () -> Long,
    loadingCalculator: LoggerLoadingCalculatorState = LoggerLoadingCalculatorState(),
    onManualLoadingCalculator: () -> Unit = {},
    onDraftLoadingCalculator: (DraftSet) -> Unit = {},
    onGuidedLoadingCalculator: (GuidedExerciseProgress) -> Unit = {},
    onLoadingCalculatorKindChanged: (LoggerLoadingCalculatorKind) -> Unit = {},
    onLoadingCalculatorEquipmentSelected: (String) -> Unit = {},
    onLoadingCalculatorTargetChanged: (String) -> Unit = {},
    onRunLoadingCalculator: () -> Unit = {},
    onDismissLoadingCalculator: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val weightFocusRequester = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateUtcMillis by remember { mutableStateOf<Long?>(null) }
    var futureTimeError by remember { mutableStateOf(false) }

    state.exerciseInstructions?.let { instructions ->
        ExerciseInstructionDialog(
            exerciseName = instructions.exerciseName,
            exerciseId = instructions.exerciseId,
            onDismiss = onDismissInstructions
        )
    }

    // One scroll container for the whole screen: a fixed-height picker inside a non-scrolling
    // parent used to push the form and the recent-sets list off short viewports.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .loggerTestTagsAsResourceId()
            .testTag(LoggerTestTags.LIST),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(Res.string.logger_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item {
            TextButton(onClick = onOpenHistory) {
                Text(stringResource(Res.string.logger_history_open))
            }
        }
        item {
            TextButton(onClick = onOpenProgress) {
                Text(stringResource(Res.string.logger_progress_open))
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        if (state.activeSession != null) {
                            Res.string.logger_session_active
                        } else {
                            Res.string.logger_session_none
                        }
                    )
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEndSession, enabled = state.activeSession != null) {
                        Text(stringResource(Res.string.logger_end_session))
                    }
                    TextButton(onClick = onNewSession) {
                        Text(stringResource(Res.string.logger_new_session))
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    state.activeSession == null -> {
                        // A session note needs an explicit session identity; the entry point is inert
                        // and explains why rather than creating a session.
                        Text(
                            text = stringResource(Res.string.logger_session_note_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onSessionNoteOpen, enabled = false) {
                            Text(stringResource(Res.string.logger_session_note_open))
                        }
                    }

                    state.sessionNoteSessionId != null -> {
                        if (state.sessionNoteLoadFailed) {
                            // The note could not be read, so an empty field would falsely read as "no
                            // note". Offer a retry instead of an editable draft.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.logger_session_note_load_failed
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = onSessionNoteRetry,
                                    enabled = !state.sessionNoteSaving
                                ) {
                                    Text(stringResource(Res.string.logger_note_retry))
                                }
                                TextButton(
                                    onClick = onSessionNoteCancel,
                                    enabled = !state.sessionNoteSaving
                                ) {
                                    Text(stringResource(Res.string.logger_cancel))
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = state.sessionNoteText,
                                onValueChange = onSessionNoteTextChanged,
                                label = {
                                    Text(stringResource(Res.string.logger_session_note_label))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4,
                                enabled = !state.sessionNoteSaving,
                                supportingText = {
                                    Text("${state.sessionNoteText.length}/$NOTE_MAX_LENGTH")
                                }
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onSessionNoteSave,
                                    enabled = !state.sessionNoteSaving
                                ) {
                                    Text(stringResource(Res.string.logger_note_save))
                                }
                                Button(
                                    onClick = onSessionNoteClear,
                                    enabled = !state.sessionNoteSaving
                                ) {
                                    Text(stringResource(Res.string.logger_note_clear))
                                }
                                TextButton(
                                    onClick = onSessionNoteCancel,
                                    enabled = !state.sessionNoteSaving
                                ) {
                                    Text(stringResource(Res.string.logger_cancel))
                                }
                            }
                            if (state.sessionNoteFailed) {
                                Text(
                                    text = stringResource(Res.string.logger_session_note_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    state.sessionNoteLoading -> {
                        Text(
                            text = stringResource(Res.string.logger_session_note_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        TextButton(onClick = onSessionNoteOpen) {
                            Text(stringResource(Res.string.logger_session_note_open))
                        }
                    }
                }
            }
        }
        item {
            // Shown independently of the editor so a successful save/clear is still confirmed after
            // the editor closes.
            if (state.sessionNoteSaved) {
                Text(
                    text = stringResource(Res.string.logger_session_note_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.todayFocus?.let { focus ->
            item {
                Text(
                    text = stringResource(
                        Res.string.logger_today,
                        stringResource(focus.labelResource())
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        item {
            if (state.noteExerciseId != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.noteLoadFailed) {
                        // The note could not be read, so an empty field would falsely read as "no
                        // note". Offer a retry instead of an editable draft.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(Res.string.logger_note_load_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onRetryNoteLoad,
                                enabled = !state.noteSaving
                            ) {
                                Text(stringResource(Res.string.logger_note_retry))
                            }
                            TextButton(
                                onClick = onCancelNote,
                                enabled = !state.noteSaving
                            ) {
                                Text(stringResource(Res.string.logger_cancel))
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = state.noteText,
                            onValueChange = onNoteTextChanged,
                            label = { Text(stringResource(Res.string.logger_note_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            supportingText = {
                                Text(
                                    text = "${state.noteText.length}/$NOTE_MAX_LENGTH"
                                )
                            }
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onSaveNote,
                                enabled = !state.noteSaving
                            ) {
                                Text(stringResource(Res.string.logger_note_save))
                            }
                            Button(
                                onClick = onClearNote,
                                enabled = !state.noteSaving
                            ) {
                                Text(stringResource(Res.string.logger_note_clear))
                            }
                            TextButton(
                                onClick = onCancelNote,
                                enabled = !state.noteSaving
                            ) {
                                Text(stringResource(Res.string.logger_cancel))
                            }
                        }
                        if (state.noteFailed) {
                            Text(
                                text = stringResource(Res.string.logger_note_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        item {
            // Shown independently of the editor so a successful save/clear is still confirmed after
            // the editor closes.
            if (state.noteSaved) {
                Text(
                    text = stringResource(Res.string.logger_note_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.exerciseSearch,
                onValueChange = onExerciseSearchChanged,
                label = { Text(stringResource(Res.string.logger_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .testTag(LoggerTestTags.EXERCISE_PICKER)
            ) {
                items(state.visibleExercises, key = { it.id }) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(LoggerTestTags.exercise(exercise.id))
                            .clickable { onExerciseSelected(exercise.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.selectedExerciseId == exercise.id,
                            onClick = { onExerciseSelected(exercise.id) }
                        )
                        Text(exercise.name)
                    }
                }
            }
        }
        if (state.selectedExerciseId != null) {
            item {
                Column {
                    if (showsTechniqueConsiderations(state.selectedExerciseId)) {
                        Text(
                            text = stringResource(Res.string.logger_technique_considerations),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = onManualInstructions,
                        modifier = Modifier.testTag(LoggerTestTags.MANUAL_INSTRUCTIONS)
                    ) {
                        Text(stringResource(Res.string.logger_instructions))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.metric == SetMetric.REPETITIONS,
                    onClick = { onMetricChanged(SetMetric.REPETITIONS) },
                    label = { Text(stringResource(Res.string.logger_reps_label)) }
                )
                FilterChip(
                    selected = state.metric == SetMetric.DURATION,
                    onClick = { onMetricChanged(SetMetric.DURATION) },
                    label = { Text(stringResource(Res.string.logger_seconds_label)) }
                )
            }
        }
        item {
            // Only the selected metric's field is shown, and only its value is ever submitted, so a
            // manual set can never record both a rep count and a duration.
            if (state.metric == SetMetric.DURATION) {
                OutlinedTextField(
                    value = state.durationSeconds,
                    onValueChange = onDurationSecondsChanged,
                    label = { Text(stringResource(Res.string.logger_seconds_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = state.reps,
                    onValueChange = onRepsChanged,
                    label = { Text(stringResource(Res.string.logger_reps_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            if (state.showWeightField) {
                OutlinedTextField(
                    value = state.weightInput,
                    onValueChange = onWeightChanged,
                    label = {
                        Text(stringResource(Res.string.logger_weight_label, state.weightUnit.label))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(weightFocusRequester)
                )
            } else {
                TextButton(onClick = onRevealWeight) {
                    Text(stringResource(Res.string.logger_add_weight))
                }
            }
        }
        if (state.availableStations.isNotEmpty()) {
            item {
                StationSelector(
                    stations = state.availableStations,
                    selectedStationId = state.selectedStationId,
                    onStationSelected = onStationSelected
                )
            }
        }
        item {
            TextButton(
                onClick = onManualLoadingCalculator,
                modifier = Modifier.testTag(LoggerLoadingCalculatorTestTags.MANUAL_ACTION)
            ) {
                Text(stringResource(Res.string.logger_loading_calculator_open))
            }
        }
        item {
            RirInput(
                value = state.rir,
                onValueChange = onRirChanged,
                onDone = { focusManager.clearFocus() }
            )
        }
        if (state.selectedExerciseIsUnilateral) {
            item {
                Text(
                    text = stringResource(Res.string.logger_per_hand),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(checked = state.isWarmup, onCheckedChange = onWarmupToggled)
                Text(stringResource(Res.string.logger_warmup))
            }
        }
        item {
            BackdatedTimeControl(
                isBackdated = state.isBackdated,
                performedAtMillis = state.performedAtMillis,
                utcOffsetMillis = state.utcOffsetMillis,
                attachesToOpenSession = state.backdatedTargetSession != null,
                forceNewSession = state.forceNewSession,
                showFutureError = futureTimeError,
                onSetTime = {
                    futureTimeError = false
                    pendingDateUtcMillis = null
                    showDatePicker = true
                },
                onUseNow = {
                    futureTimeError = false
                    onClearBackdated()
                },
                onForceNewSessionChanged = onForceNewSessionChanged
            )
        }
        item {
            Button(onClick = onLog, enabled = state.canLog && !state.manualLogInProgress) {
                Text(stringResource(Res.string.logger_log_button))
            }
        }
        if (state.manualLogFailed) {
            item {
                Text(
                    text = stringResource(Res.string.logger_log_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (state.manualLogRefreshFailed) {
            item {
                Text(
                    text = stringResource(Res.string.logger_log_refresh_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        state.activeOccurrence?.let { occurrence ->
            item {
                ActiveOccurrenceSection(
                    occurrence = occurrence,
                    message = state.occurrenceMessage,
                    onFinish = onFinishWorkout,
                    onFinishPartially = onFinishWorkoutPartially,
                    onSkip = onSkipWorkout,
                    onMessageShown = onOccurrenceMessageShown
                )
            }
        }
        if (state.isGuidedActive) {
            state.shortRestCoachingExerciseName?.let { name ->
                item {
                    Column {
                        Text(stringResource(Res.string.logger_short_rest_coaching, name))
                        TextButton(onClick = onDismissShortRestCoaching) {
                            Text(stringResource(Res.string.logger_short_rest_dismiss))
                        }
                    }
                }
            }
            val warmup = state.visibleWarmupView
            if (warmup != null) {
                item {
                    WarmupGuidanceSection(
                        view = warmup,
                        checked = state.warmupPreparationChecked,
                        weightUnit = state.weightUnit,
                        writeInProgress = state.draftWriteInProgress,
                        rampWriteFailed = state.warmupRampWriteFailed,
                        onPreparationToggled = onWarmupPreparationToggled,
                        onEditPreparation = onEditWarmupPreparation,
                        onSkipPreparation = onSkipWarmupPreparation,
                        onRestoreSkipped = onRestoreSkippedWarmupPreparation,
                        onLogRamp = onLogWarmupRamp,
                        onEditRamp = onEditWarmupRamp,
                        onHide = onToggleWarmupGuidance
                    )
                }
            } else if (state.warmupGuidanceHidden && state.warmupGuidance?.isEmpty == false) {
                item {
                    TextButton(onClick = onToggleWarmupGuidance) {
                        Text(stringResource(Res.string.logger_warmup_guidance_show))
                    }
                }
            }
            state.guidedProgress?.let { guided ->
                item {
                    GuidedWorkoutSection(
                        guided = guided,
                        weightUnit = state.weightUnit,
                        exerciseLoadBasis = state.exerciseLoadBasis,
                        writeFailed = state.guidedSetWriteFailed,
                        writeInProgress = state.draftWriteInProgress,
                        restTimer = state.restTimer,
                        restDurationSeconds = state.restDurationSeconds,
                        restDurationIsOverride = state.restDurationIsOverride,
                        restDurationIsPairRest = state.restDurationIsPairRest,
                        restDurationIsSupersetTransition = state.restDurationIsSupersetTransition,
                        onRestDurationChanged = onRestDurationChanged,
                        onResetRestDuration = onResetRestDuration,
                        onCancelRestTimer = onCancelRestTimer,
                        onLogSet = onConfirmGuidedSet,
                        onLogSetNow = onConfirmGuidedSetNow,
                        startedSetEntryId = state.startedSetEntryId,
                        frozenEntryIds = state.frozenEntryIds,
                        onStartSet = onStartGuidedSet,
                        onEditSet = onEditGuidedSet,
                        onReplace = onReplaceOccurrenceEntry,
                        onInstructions = onGuidedInstructions,
                        onLoadingCalculator = onGuidedLoadingCalculator,
                        expandedEntryIds = guided.exercises
                            .filter(state::isGuidedEntryExpanded)
                            .map { it.occurrenceEntryId }
                            .toSet(),
                        actionEntryIds = guided.exercises
                            .filter(state::showsGuidedActions)
                            .map { it.occurrenceEntryId }
                            .toSet(),
                        supersetControlEntryIds = guided.exercises
                            .filter(state::showsSupersetControls)
                            .map { it.occurrenceEntryId }
                            .toSet(),
                        onToggleExpanded = onToggleGuidedExpanded,
                        nextEntryId = nextGuidedEntryId,
                        skippedEntryId = supersetSkippedEntryId,
                        onSkipTurn = onSkipSupersetTurn,
                        onResumeTurn = onResumeSupersetTurn,
                        onUnpair = onUnpairSuperset
                    )
                }
            }
        } else if (state.draftSets.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.logger_planned_today),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = onConfirmAllDrafts,
                        enabled = !state.draftWriteInProgress
                    ) {
                        Text(stringResource(Res.string.logger_confirm_all))
                    }
                }
            }
            items(state.draftSets) { draft ->
                val weight = loadWeightText(
                    draft.loadKind,
                    draft.weightKg,
                    state.weightUnit,
                    state.exerciseLoadBasis[draft.exerciseId]
                )
                var expanded by remember(draft) { mutableStateOf(false) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (draft.durationSeconds != null) {
                                stringResource(
                                    Res.string.logger_draft_timed_line,
                                    draft.name,
                                    draft.sets,
                                    draft.durationSeconds,
                                    weight
                                )
                            } else {
                                "${draft.name}  ${draft.sets} x ${draft.reps} · ~$weight"
                            }
                        )
                        draft.setTargets?.let { targets ->
                            val rirs = targets.targets.mapNotNull { it.targetRir }.distinct()
                            if (rirs.size == 1) {
                                val representative = targets.targets.first {
                                    it.targetRir == rirs.single()
                                }
                                representative.equivalentRpe?.let { rpe ->
                                    Text(
                                        text = stringResource(
                                            Res.string.logger_draft_prescribed_effort,
                                            rirs.single(),
                                            rpe
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else if (rirs.size > 1) {
                                Text(
                                    text = stringResource(
                                        Res.string.logger_draft_prescribed_effort_varies
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                text = setTargetsSummaryLine(
                                    targets,
                                    state.weightUnit,
                                    state.exerciseLoadBasis[draft.exerciseId]
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TargetExplanationDisclosure(
                            key = draft.occurrenceEntryId ?: draft.exerciseId,
                            explanation = draft.explanation,
                            weightUnit = state.weightUnit
                        )
                    }
                    if (showsTechniqueConsiderations(draft.exerciseId)) {
                        Text(
                            text = stringResource(Res.string.logger_technique_considerations),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { onConfirmDraft(draft) },
                            enabled = !state.draftWriteInProgress
                        ) {
                            Text(stringResource(Res.string.logger_confirm))
                        }
                        TextButton(onClick = { onDismissDraft(draft) }) {
                            Text(stringResource(Res.string.logger_dismiss))
                        }
                        TextButton(
                            onClick = { onDraftInstructions(draft) },
                            modifier = Modifier.testTag(LoggerTestTags.draftInstructions(draft))
                        ) {
                            Text(stringResource(Res.string.logger_instructions))
                        }
                        if (loggerLoadingTargetEligible(draft.loadKind, draft.weightKg)) {
                            TextButton(
                                onClick = { onDraftLoadingCalculator(draft) },
                                modifier = Modifier.testTag(
                                    LoggerLoadingCalculatorTestTags.draftAction(
                                        draft.occurrenceEntryId?.toString() ?: draft.exerciseId
                                    )
                                )
                            ) {
                                Text(stringResource(Res.string.logger_loading_calculator_action))
                            }
                        }
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(stringResource(Res.string.logger_draft_more_options))
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                draft.occurrenceEntryId?.let { entryId ->
                                    if (state.canReplaceEntry(entryId)) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(Res.string.logger_replace))
                                            },
                                            enabled = !state.draftWriteInProgress,
                                            onClick = {
                                                expanded = false
                                                onReplaceOccurrenceEntry(entryId)
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.logger_draft_edit)) },
                                    onClick = {
                                        expanded = false
                                        onEditDraft(draft)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.draftWriteRetries.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(
                        if (state.draftWriteRetries.any { it.anyRecorded }) {
                            Res.string.logger_draft_write_partial
                        } else {
                            Res.string.logger_draft_write_none
                        }
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Text(
                text = stringResource(Res.string.logger_recent),
                style = MaterialTheme.typography.titleMedium
            )
        }
        itemsIndexed(state.recentSets) { index, row ->
            Column {
                if (row.startsSessionAfter(state.recentSets.getOrNull(index - 1))) {
                    Text(
                        text = stringResource(Res.string.logger_session_recorded),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() }
                    )
                }
                val unit = state.weightUnit
                val weight = loadWeightText(row.loadKind, row.weightKg, unit)
                val warmupSuffix = if (row.isWarmup) {
                    " " + stringResource(Res.string.logger_warmup_suffix)
                } else {
                    ""
                }
                val weekDay = if (row.weekNumber != null && row.dayIndex != null) {
                    " · " + stringResource(
                        Res.string.logger_set_week_day,
                        row.weekNumber,
                        row.dayIndex + 1
                    )
                } else {
                    ""
                }
                val measurement = when {
                    row.durationSeconds != null -> stringResource(
                        Res.string.logger_history_set_duration_line,
                        row.durationSeconds,
                        weight
                    )

                    row.reps != null -> stringResource(
                        Res.string.logger_history_set_line,
                        row.reps,
                        weight
                    )

                    else -> weight
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRecentSetSelected(row) }
                ) {
                    Text(
                        text = "${row.exerciseName}  $measurement$warmupSuffix$weekDay",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onEditLoggedSet(row) }
                    ) {
                        Text(stringResource(Res.string.logger_edit_set))
                    }
                    TextButton(onClick = { onDeleteSet(row.id) }) {
                        Text(stringResource(Res.string.logger_delete_set))
                    }
                }
                if (row.sessionId == null) {
                    Text(
                        text = stringResource(Res.string.logger_session_not_recorded),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    val backdatedAnchor = state.performedAtMillis ?: nowMillis()
    val pickerAnchor = backdatedAnchor
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = pendingDateUtcMillis
                ?: localDateStartOfDayUtcMillis(pickerAnchor, state.utcOffsetMillis)
        )
        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selected ->
                            pendingDateUtcMillis = selected
                            showDatePicker = false
                            showTimePicker = true
                        }
                    }
                ) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showTimePicker) {
        val anchorParts = localDateTimeParts(pickerAnchor, state.utcOffsetMillis)
        val timePickerState = rememberTimePickerState(
            initialHour = anchorParts.hour,
            initialMinute = anchorParts.minute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismissRequest = {
                showTimePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateUtcMillis?.let { date ->
                            futureTimeError = !onBackdatedDateTimePicked(
                                date,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                        }
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                    }
                ) {
                    Text(stringResource(Res.string.logger_cancel))
                }
            },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) }
        ) {
            TimePicker(state = timePickerState)
        }
    }
    state.loggedSetEdit?.let { edit ->
        LoggedSetEditDialog(
            edit = edit,
            saving = state.savingLoggedSet,
            failed = state.loggedSetEditFailed,
            utcOffsetMillis = state.utcOffsetMillis,
            nowMillis = nowMillis,
            onRepsChanged = onLoggedSetRepsChanged,
            onDurationChanged = onLoggedSetDurationChanged,
            onMetricChanged = onLoggedSetMetricChanged,
            onWeightChanged = onLoggedSetWeightChanged,
            onRirChanged = onLoggedSetRirChanged,
            onTimeChanged = onLoggedSetTimeChanged,
            onCancel = onCancelLoggedSetEdit,
            onSave = onSaveLoggedSetEdit
        )
    }
    state.warmupEditor?.let { editor ->
        WarmupGuidanceEditorDialog(
            editor = editor,
            // The editor's frozen unit keeps the label, the typed value and the ceiling consistent.
            weightUnit = editor.weightUnit,
            onRepsChanged = onWarmupEditorRepsChanged,
            onDurationChanged = onWarmupEditorDurationChanged,
            onWeightChanged = onWarmupEditorWeightChanged,
            onReset = onResetWarmupEditor,
            onCancel = onCancelWarmupEditor,
            onSave = onSaveWarmupEditor
        )
    }
    state.legacyResolution?.let { resolution ->
        val draft = resolution.current.draft
        val recorded = draft.weightKg
            ?.let {
                formatWeight(state.weightUnit.kilogramsToDisplay(it)) +
                    " " + state.weightUnit.label
            }
            ?: stringResource(Res.string.logger_weight_none)
        AlertDialog(
            onDismissRequest = onDismissLegacyResolution,
            title = { Text(stringResource(Res.string.logger_load_confirm_title)) },
            text = {
                Text(stringResource(Res.string.logger_load_confirm_body, draft.name, recorded))
            },
            confirmButton = {
                if (resolution.current.canBeExternal) {
                    TextButton(onClick = onResolveLegacyAsExternal) {
                        Text(stringResource(Res.string.logger_load_use_external))
                    }
                } else {
                    TextButton(onClick = onResolveLegacyAsBodyweight) {
                        Text(stringResource(Res.string.logger_load_use_bodyweight))
                    }
                }
            },
            dismissButton = {
                Row {
                    if (resolution.current.canBeExternal) {
                        TextButton(onClick = onResolveLegacyAsBodyweight) {
                            Text(stringResource(Res.string.logger_load_use_bodyweight))
                        }
                    }
                    TextButton(onClick = onDismissLegacyResolution) {
                        Text(stringResource(Res.string.logger_cancel))
                    }
                }
            }
        )
    }

    if (state.legacyResolution == null) {
        state.draftEdit?.let { edit ->
            val capability = edit.draft.loadCapability
            DraftEditDialog(
                edit = edit,
                // The label follows the unit the editor captured, exactly as its parser does, so a
                // preference change never relabels the same digits as another unit.
                weightUnit = edit.weightUnit,
                utcOffsetMillis = state.utcOffsetMillis,
                nowMillis = nowMillis(),
                canRevealWeight = capability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
                showWeight = when (capability) {
                    ExerciseLoadCapability.BODYWEIGHT_ONLY -> false
                    ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> edit.weightRevealed
                    else -> true
                },
                // A guided draft's editor only applies values to the pending set; logging is separate.
                appliesOnly = state.isGuidedActive && edit.draft.occurrenceEntryId != null,
                onRepsChanged = onDraftRepsChanged,
                onDurationSecondsChanged = onDraftDurationSecondsChanged,
                onWeightChanged = onDraftWeightChanged,
                onRirChanged = onDraftRirChanged,
                onRevealWeight = onDraftWeightRevealed,
                onTimeChanged = onDraftPerformedAtChanged,
                onCancel = onCancelDraftEdit,
                onReset = onResetDraftEdit,
                onConfirm = onConfirmDraftEdit
            )
        }
    }
    state.missingLoadPrompt?.let { draft ->
        AlertDialog(
            onDismissRequest = onCancelMissingLoadPrompt,
            title = { Text(stringResource(Res.string.logger_draft_load_prompt_title)) },
            text = { Text(stringResource(Res.string.logger_draft_load_prompt_body, draft.name)) },
            confirmButton = {
                TextButton(onClick = onUseLastLoggedLoad) {
                    Text(stringResource(Res.string.logger_draft_load_use_last))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = onEnterMissingLoad) {
                        Text(stringResource(Res.string.logger_draft_load_enter))
                    }
                    TextButton(onClick = onLogMissingLoadWithoutWeight) {
                        Text(stringResource(Res.string.logger_draft_load_without))
                    }
                    TextButton(onClick = onCancelMissingLoadPrompt) {
                        Text(stringResource(Res.string.logger_cancel))
                    }
                }
            }
        )
    }
    if (loadingCalculator.open) {
        LoggerLoadingCalculatorDialog(
            state = loadingCalculator,
            onKindChanged = onLoadingCalculatorKindChanged,
            onEquipmentSelected = onLoadingCalculatorEquipmentSelected,
            onTargetChanged = onLoadingCalculatorTargetChanged,
            onRun = onRunLoadingCalculator,
            onDismiss = onDismissLoadingCalculator
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DraftEditDialog(
    edit: DraftEdit,
    weightUnit: WeightUnit,
    utcOffsetMillis: Long,
    nowMillis: Long,
    canRevealWeight: Boolean,
    showWeight: Boolean,
    appliesOnly: Boolean,
    onRepsChanged: (String) -> Unit,
    onDurationSecondsChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onRirChanged: (String) -> Unit,
    onRevealWeight: () -> Unit,
    onTimeChanged: (Long?) -> Boolean,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<Long?>(null) }
    var timeError by remember { mutableStateOf(false) }
    val anchor = edit.performedAtMillis ?: nowMillis
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.logger_draft_edit_title, edit.draft.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Only the draft's frozen measure is shown and submitted, so a timed draft
                // edits seconds and a counted draft edits repetitions — never both.
                if (edit.isTimed) {
                    OutlinedTextField(
                        value = edit.durationSeconds,
                        onValueChange = onDurationSecondsChanged,
                        label = { Text(stringResource(Res.string.logger_seconds_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = edit.reps,
                        onValueChange = onRepsChanged,
                        label = { Text(stringResource(Res.string.logger_reps_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                if (showWeight) {
                    OutlinedTextField(
                        value = edit.weightInput,
                        onValueChange = onWeightChanged,
                        label = {
                            Text(stringResource(Res.string.logger_weight_label, weightUnit.label))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                } else {
                    Text(stringResource(Res.string.logger_load_bodyweight))
                    if (canRevealWeight) {
                        TextButton(onClick = onRevealWeight) {
                            Text(stringResource(Res.string.logger_add_weight))
                        }
                    }
                }
                RirInput(
                    value = edit.rir,
                    onValueChange = onRirChanged
                )
                TextButton(onClick = {
                    showDate = true
                    timeError = false
                }) {
                    Text(stringResource(Res.string.logger_set_time))
                }
                TextButton(onClick = {
                    timeError = false
                    onTimeChanged(null)
                }) {
                    Text(stringResource(Res.string.logger_use_now))
                }
                if (timeError) Text(stringResource(Res.string.logger_future_time_error))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = edit.enteredMetricIsPositive) {
                Text(
                    stringResource(
                        if (appliesOnly) {
                            Res.string.logger_draft_edit_apply
                        } else {
                            Res.string.logger_confirm
                        }
                    )
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.logger_draft_reset))
                }
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.logger_cancel)) }
            }
        }
    )
    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis =
            date ?: localDateStartOfDayUtcMillis(anchor, utcOffsetMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    date = picker.selectedDateMillis
                    showDate = false
                    showTime =
                        true
                }) {
                    Text(stringResource(Res.string.logger_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDate = false
                }) { Text(stringResource(Res.string.logger_cancel)) }
            }
        ) { DatePicker(state = picker) }
    }
    if (showTime) {
        val parts = localDateTimeParts(anchor, utcOffsetMillis)
        val picker =
            rememberTimePickerState(
                initialHour = parts.hour,
                initialMinute = parts.minute,
                is24Hour = true
            )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = date
                    if (selectedDate != null) {
                        val picked =
                            pickedLocalDateTimeToEpochMillis(
                                selectedDate,
                                picker.hour,
                                picker.minute,
                                utcOffsetMillis
                            )
                        timeError = !onTimeChanged(picked)
                    }
                    showTime = false
                }) { Text(stringResource(Res.string.logger_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTime = false
                }) { Text(stringResource(Res.string.logger_cancel)) }
            },
            title = { Text(stringResource(Res.string.logger_pick_time_title)) }
        ) { TimePicker(state = picker) }
    }
}

/**
 * What produced this frozen target, with the recorded facts behind a disclosure. A slot with no
 * record — a replacement, an edit, or a manual routine — says nothing rather than implying a reason
 * was lost.
 */
@Composable
private fun TargetExplanationDisclosure(
    key: Any,
    explanation: ExerciseTargetExplanation?,
    weightUnit: WeightUnit
) {
    val view = explanation?.toExplanationView() ?: return
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
                        Res.string.logger_explanation_hide_details
                    } else {
                        Res.string.logger_explanation_details
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
            Res.string.logger_explanation_kilograms,
            formatWeight(weightUnit.kilogramsToDisplay(value)),
            weightUnit.label
        )
        TargetExplanationValueKind.PERCENT -> formatWeight(value * 100.0) + "%"
        else -> formatWeight(value)
    }
    return stringResource(Res.string.logger_explanation_line, label, rendered)
}

private fun TargetExplanationReason.labelResource(): StringResource = when (this) {
    TargetExplanationReason.SUMMARY_AI_PROPOSAL -> Res.string.logger_explanation_summary_ai
    TargetExplanationReason.SUMMARY_CALCULATED -> Res.string.logger_explanation_summary_calculated
    TargetExplanationReason.SUMMARY_SETS_ONLY -> Res.string.logger_explanation_summary_sets_only
    TargetExplanationReason.SUMMARY_UNAVAILABLE_NOT_LOAD_TRACKED ->
        Res.string.logger_explanation_unavailable_not_load_tracked
    TargetExplanationReason.SUMMARY_UNAVAILABLE_INSUFFICIENT_EVIDENCE ->
        Res.string.logger_explanation_unavailable_insufficient_evidence
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_NOT_SHARED ->
        Res.string.logger_explanation_unavailable_ai_not_shared
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_OMITTED ->
        Res.string.logger_explanation_unavailable_ai_omitted
    TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_REJECTED ->
        Res.string.logger_explanation_unavailable_ai_rejected
    TargetExplanationReason.BASELINE_RECENT_ESTIMATE ->
        Res.string.logger_explanation_baseline_recent_estimate
    TargetExplanationReason.BASELINE_MANUAL_FLOOR ->
        Res.string.logger_explanation_baseline_manual_floor
    TargetExplanationReason.BASELINE_QUALIFYING_SETS ->
        Res.string.logger_explanation_baseline_qualifying_sets
    TargetExplanationReason.BASELINE_EFFECTIVE -> Res.string.logger_explanation_baseline_effective
    TargetExplanationReason.PROGRESSION_APPLIED ->
        Res.string.logger_explanation_progression_applied
    TargetExplanationReason.PROGRESSION_DELTA -> Res.string.logger_explanation_progression_delta
    TargetExplanationReason.PROGRESSION_SUCCESS_STREAK_SIZE ->
        Res.string.logger_explanation_progression_success_streak_size
    TargetExplanationReason.PROGRESSION_FAILURE_STREAK_SIZE ->
        Res.string.logger_explanation_progression_failure_streak_size
    TargetExplanationReason.PROGRESSION_NONE_NO_STREAK ->
        Res.string.logger_explanation_progression_none_no_streak
    TargetExplanationReason.PROGRESSION_HELD_AFTER_MISSES ->
        Res.string.logger_explanation_progression_held_after_misses
    TargetExplanationReason.PROGRESSION_NONE_NO_PRESCRIPTION ->
        Res.string.logger_explanation_progression_none_no_prescription
    TargetExplanationReason.PROGRESSION_PAUSED_DELOAD ->
        Res.string.logger_explanation_progression_paused_deload
    TargetExplanationReason.CONVERSION_REPS -> Res.string.logger_explanation_conversion_reps
    TargetExplanationReason.CONVERSION_DELOAD_SCALE ->
        Res.string.logger_explanation_conversion_deload_scale
    TargetExplanationReason.CONVERSION_INTENSITY ->
        Res.string.logger_explanation_conversion_intensity
    TargetExplanationReason.CONVERSION_ROUNDED_TO ->
        Res.string.logger_explanation_conversion_rounded_to
    TargetExplanationReason.CONVERSION_BEFORE_CEILING ->
        Res.string.logger_explanation_conversion_before_ceiling
    TargetExplanationReason.CONVERSION_CEILING -> Res.string.logger_explanation_conversion_ceiling
    TargetExplanationReason.CONVERSION_CEILING_BINDS ->
        Res.string.logger_explanation_conversion_ceiling_binds
    TargetExplanationReason.AI_PROPOSED -> Res.string.logger_explanation_ai_proposed
    TargetExplanationReason.AI_ACCEPTED -> Res.string.logger_explanation_ai_accepted
    TargetExplanationReason.AI_NOT_ACCEPTED -> Res.string.logger_explanation_ai_not_accepted
    TargetExplanationReason.AI_BOUNDED_BY_PROGRESSION ->
        Res.string.logger_explanation_ai_bounded_by_progression
    TargetExplanationReason.AI_CAPPED_BY_EQUIPMENT ->
        Res.string.logger_explanation_ai_capped_by_equipment
    TargetExplanationReason.AI_EQUIPMENT_CEILING ->
        Res.string.logger_explanation_ai_equipment_ceiling
    TargetExplanationReason.AI_CEILING_NOT_BINDING ->
        Res.string.logger_explanation_ai_ceiling_not_binding
    TargetExplanationReason.AI_DELOAD_SCALE -> Res.string.logger_explanation_ai_deload_scale
    TargetExplanationReason.AI_DELOAD_SCALED -> Res.string.logger_explanation_ai_deload_scaled
    TargetExplanationReason.SETS_BASE -> Res.string.logger_explanation_sets_base
    TargetExplanationReason.SETS_DELOAD_SCALE -> Res.string.logger_explanation_sets_deload_scale
    TargetExplanationReason.SETS_REDUCED_FOR_FATIGUE ->
        Res.string.logger_explanation_sets_reduced_for_fatigue
    TargetExplanationReason.SETS_FINAL -> Res.string.logger_explanation_sets_final
}

/**
 * A compact read-only summary of a draft's frozen ordered targets, one entry per set. It only reads
 * the draft the screen already renders; a prescribed target is never written back as actual effort.
 */
@Composable
private fun setTargetsSummaryLine(
    targets: SlotSetTargets,
    unit: WeightUnit,
    basis: LoadBasis? = null
): String {
    val list = targets.targets
    val parts = mutableListOf<String>()
    for (index in list.indices) {
        val target = list[index]
        val load = loadWeightText(target.loadKind, target.weightKg, unit, basis)
        val rir = target.targetRir
        parts.add(
            if (rir == null) {
                stringResource(
                    Res.string.logger_draft_set_target,
                    index + 1,
                    target.reps ?: 0,
                    load
                )
            } else {
                stringResource(
                    Res.string.logger_draft_set_target_rir,
                    index + 1,
                    target.reps ?: 0,
                    load,
                    rir
                )
            }
        )
    }
    return stringResource(Res.string.logger_draft_set_targets_summary, parts.joinToString(", "))
}

/** Renders a recorded weight with honest load semantics (external vs added vs bodyweight vs legacy). */
@Composable
internal fun loadWeightText(
    loadKind: LoadKind,
    weightKg: Double?,
    unit: WeightUnit,
    basis: LoadBasis? = null
): String = when (val display = loadDisplayFor(loadKind, weightKg)) {
    LoadDisplay.None -> stringResource(Res.string.logger_weight_none)
    is LoadDisplay.External -> {
        val weight = formatWeight(unit.kilogramsToDisplay(display.weightKg)) + " " + unit.label
        basis?.let { label ->
            stringResource(Res.string.logger_load_basis, weight, basisLabel(label))
        } ?: weight
    }
    is LoadDisplay.Added -> display.weightKg?.let {
        stringResource(
            Res.string.logger_load_added,
            formatWeight(unit.kilogramsToDisplay(it)),
            unit.label
        )
    } ?: stringResource(Res.string.logger_load_added_none)
    LoadDisplay.Bodyweight -> stringResource(Res.string.logger_load_bodyweight)
    is LoadDisplay.LegacyUnconfirmed -> display.weightKg?.let {
        stringResource(
            Res.string.logger_load_legacy,
            formatWeight(unit.kilogramsToDisplay(it)),
            unit.label
        )
    } ?: stringResource(Res.string.logger_weight_none)
}

@Composable
private fun basisLabel(basis: LoadBasis): String = stringResource(
    when (basis) {
        LoadBasis.PER_HAND -> Res.string.logger_load_basis_per_hand
        LoadBasis.PER_DUMBBELL -> Res.string.logger_load_basis_per_dumbbell
    }
)

/**
 * The known reason a numeric target is absent, or null when the recorded explanation does not
 * distinguish one. Absence alone never becomes a claim about inventory or history.
 */
internal enum class NoLoadReason { INVENTORY, INSUFFICIENT_HISTORY }

/**
 * Classifies [explanation] for the missing-load line. A converted working number and a sanitized
 * AI number that was accepted and then withheld by the saved implement are both the inventory's
 * limit; missing history stays its own reason, and everything else stays silent.
 */
internal fun noLoadReason(explanation: ExerciseTargetExplanation?): NoLoadReason? = when {
    explanation == null -> null
    explanation.unavailableReason == LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE ->
        NoLoadReason.INSUFFICIENT_HISTORY

    explanation.conversion != null || explanation.aiAdjustment?.proposedAccepted == true ->
        NoLoadReason.INVENTORY

    else -> null
}

@Composable
internal fun noLoadReasonText(explanation: ExerciseTargetExplanation?): String? =
    when (noLoadReason(explanation)) {
        NoLoadReason.INVENTORY -> stringResource(Res.string.logger_no_load_inventory)
        NoLoadReason.INSUFFICIENT_HISTORY -> stringResource(Res.string.logger_no_load_history)
        null -> null
    }

@Composable
private fun BackdatedTimeControl(
    isBackdated: Boolean,
    performedAtMillis: Long?,
    utcOffsetMillis: Long,
    attachesToOpenSession: Boolean,
    forceNewSession: Boolean,
    showFutureError: Boolean,
    onSetTime: () -> Unit,
    onUseNow: () -> Unit,
    onForceNewSessionChanged: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isBackdated && performedAtMillis != null) {
                    val parts = localDateTimeParts(performedAtMillis, utcOffsetMillis)
                    stringResource(
                        Res.string.logger_backdated_at,
                        localDateLabel(parts),
                        localTimeLabel(parts)
                    )
                } else {
                    stringResource(Res.string.logger_time_live)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSetTime) {
                    Text(stringResource(Res.string.logger_set_time))
                }
                if (isBackdated) {
                    TextButton(onClick = onUseNow) {
                        Text(stringResource(Res.string.logger_use_now))
                    }
                }
            }
        }
        if (isBackdated) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        Res.string.logger_target_label,
                        stringResource(
                            if (attachesToOpenSession) {
                                Res.string.logger_target_current_session
                            } else {
                                Res.string.logger_new_session
                            }
                        )
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(Res.string.logger_new_session))
                    Switch(checked = forceNewSession, onCheckedChange = onForceNewSessionChanged)
                }
            }
        }
        if (showFutureError) {
            Text(
                text = stringResource(Res.string.logger_future_time_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal fun localDateLabel(parts: LocalDateTimeParts): String =
    "${parts.year}-${twoDigits(parts.month)}-${twoDigits(parts.day)}"

internal fun localTimeLabel(parts: LocalDateTimeParts): String =
    "${twoDigits(parts.hour)}:${twoDigits(parts.minute)}"

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun SplitFocus.labelResource(): StringResource = when (this) {
    SplitFocus.PUSH -> Res.string.focus_push
    SplitFocus.PULL -> Res.string.focus_pull
    SplitFocus.LEGS -> Res.string.focus_legs
    SplitFocus.UPPER -> Res.string.focus_upper
    SplitFocus.LOWER -> Res.string.focus_lower
    SplitFocus.FULL_BODY -> Res.string.focus_full_body
}

@Composable
private fun ActiveOccurrenceSection(
    occurrence: ActiveOccurrence,
    message: String?,
    onFinish: () -> Unit,
    onFinishPartially: () -> Unit,
    onSkip: () -> Unit,
    onMessageShown: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(Res.string.logger_active_workout, occurrence.workoutName),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    Res.string.logger_active_progress,
                    occurrence.performedSets,
                    occurrence.prescribedSets
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(Res.string.logger_finish))
                }
                TextButton(onClick = onFinishPartially) {
                    Text(stringResource(Res.string.logger_finish_partial))
                }
                TextButton(onClick = onSkip) {
                    Text(stringResource(Res.string.logger_skip))
                }
            }
            message?.let { error ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onMessageShown) {
                        Text(stringResource(Res.string.logger_dismiss))
                    }
                }
            }
        }
    }
}

/**
 * The optional warm-up guidance for the active guided workout: a preparation check-list and the
 * suggested ramp sets per movement pattern. It suggests only and never edits the frozen
 * prescription; transient edits live in the Logger's in-memory state.
 */
@Composable
private fun WarmupGuidanceSection(
    view: WarmupGuidanceView,
    checked: Set<WarmupAction>,
    weightUnit: WeightUnit,
    writeInProgress: Boolean,
    rampWriteFailed: Boolean,
    onPreparationToggled: (WarmupAction) -> Unit,
    onEditPreparation: (WarmupAction) -> Unit,
    onSkipPreparation: (WarmupAction) -> Unit,
    onRestoreSkipped: () -> Unit,
    onLogRamp: (Long, Int) -> Unit,
    onEditRamp: (Long, Int) -> Unit,
    onHide: () -> Unit
) {
    Card(modifier = Modifier.testTag(LoggerTestTags.WARMUP_CARD)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.logger_warmup_guidance_title),
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onHide) {
                    Text(stringResource(Res.string.logger_warmup_guidance_hide))
                }
            }
            Text(
                text = stringResource(Res.string.logger_warmup_guidance_note),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(Res.string.logger_warmup_guidance_lock_note),
                style = MaterialTheme.typography.bodySmall
            )
            if (rampWriteFailed) {
                Text(
                    text = stringResource(Res.string.logger_warmup_guidance_ramp_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (view.preparation.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.logger_warmup_guidance_preparation),
                    style = MaterialTheme.typography.titleSmall
                )
                view.preparation.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // The checkbox and its line form one labeled toggle, so the preparation action
                        // is announced together with its checked state; Edit and Skip stay separate.
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .testTag(LoggerTestTags.preparationToggle(row.action))
                                .toggleable(
                                    value = row.action in checked,
                                    role = Role.Checkbox,
                                    onValueChange = { onPreparationToggled(row.action) }
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(checked = row.action in checked, onCheckedChange = null)
                            Text(text = warmupPreparationText(row))
                        }
                        TextButton(
                            onClick = { onEditPreparation(row.action) },
                            modifier = Modifier.testTag(LoggerTestTags.preparationEdit(row.action))
                        ) {
                            Text(stringResource(Res.string.logger_warmup_guidance_edit))
                        }
                        TextButton(
                            onClick = { onSkipPreparation(row.action) },
                            modifier = Modifier.testTag(LoggerTestTags.preparationSkip(row.action))
                        ) {
                            Text(stringResource(Res.string.logger_warmup_guidance_skip))
                        }
                    }
                }
            }
            // Restore stays reachable even when every preparation row is currently skipped.
            if (view.skippedPreparationCount > 0) {
                TextButton(onClick = onRestoreSkipped) {
                    Text(stringResource(Res.string.logger_warmup_guidance_restore_skipped))
                }
            }
            if (view.ramps.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.logger_warmup_guidance_ramps_title),
                    style = MaterialTheme.typography.titleSmall
                )
                view.ramps.forEach { plan ->
                    Column {
                        Text(plan.exerciseName)
                        plan.rows.forEach { row ->
                            val entryId = plan.occurrenceEntryId
                            val rampIndex = row.key.rampIndex
                            Column(
                                modifier = Modifier.testTag(
                                    LoggerTestTags.rampRow(entryId, rampIndex)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = warmupRampText(row, weightUnit),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { onEditRamp(entryId, rampIndex) },
                                        modifier = Modifier.testTag(
                                            LoggerTestTags.rampEdit(entryId, rampIndex)
                                        )
                                    ) {
                                        Text(stringResource(Res.string.logger_warmup_guidance_edit))
                                    }
                                    TextButton(
                                        onClick = { onLogRamp(entryId, rampIndex) },
                                        enabled = !writeInProgress && !row.exceedsCeiling,
                                        modifier = Modifier.testTag(
                                            LoggerTestTags.rampLog(entryId, rampIndex)
                                        )
                                    ) {
                                        Text(
                                            stringResource(
                                                Res.string.logger_warmup_guidance_log_ramp
                                            )
                                        )
                                    }
                                }
                                if (row.exceedsCeiling) {
                                    Text(
                                        text = stringResource(
                                            Res.string.logger_warmup_guidance_editor_weight_exceeds,
                                            loadWeightText(
                                                LoadKind.EXTERNAL,
                                                row.ceilingKg,
                                                weightUnit
                                            )
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The localized preparation line: a timed item shows minutes, a practice item shows reps. */
@Composable
private fun warmupPreparationText(row: WarmupPreparationRow): String {
    val label = stringResource(row.action.labelResource())
    val reps = row.reps
    val durationSeconds = row.durationSeconds
    val base = when {
        durationSeconds != null -> stringResource(
            Res.string.logger_warmup_guidance_item_timed,
            label,
            durationSeconds / 60
        )
        reps != null -> stringResource(Res.string.logger_warmup_guidance_item_reps, label, reps)
        else -> label
    }
    return if (row.edited) {
        base + stringResource(Res.string.logger_warmup_guidance_edited_suffix)
    } else {
        base
    }
}

/**
 * A ramp line keeps its default percentage until it is edited or the equipment ceiling lowered its
 * load, then shows only its actual values.
 */
@Composable
private fun warmupRampText(row: WarmupRampRow, weightUnit: WeightUnit): String {
    val load = loadWeightText(LoadKind.EXTERNAL, row.weightKg, weightUnit)
    val percent = row.percentOfWorkingWeight
    return when {
        percent == null -> stringResource(
            Res.string.logger_warmup_guidance_ramp_line_edited,
            row.reps,
            load
        )
        row.isEquipmentLimited -> stringResource(
            Res.string.logger_warmup_guidance_ramp_line_limited,
            row.reps,
            load
        )
        else -> stringResource(
            Res.string.logger_warmup_guidance_ramp_line,
            "$percent%",
            row.reps,
            load
        )
    }
}

/** The transient editor for one preparation or ramp value; Save changes only the in-memory guidance. */
@Composable
private fun WarmupGuidanceEditorDialog(
    editor: WarmupGuidanceEditor,
    weightUnit: WeightUnit,
    onRepsChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier
            .loggerTestTagsAsResourceId()
            .testTag(LoggerTestTags.WARMUP_EDITOR),
        title = { Text(stringResource(Res.string.logger_warmup_guidance_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editor.timed) {
                    OutlinedTextField(
                        value = editor.durationInput,
                        onValueChange = onDurationChanged,
                        label = {
                            Text(stringResource(Res.string.logger_warmup_guidance_editor_minutes))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag(LoggerTestTags.WARMUP_EDITOR_DURATION)
                    )
                } else {
                    OutlinedTextField(
                        value = editor.repsInput,
                        onValueChange = onRepsChanged,
                        label = { Text(stringResource(Res.string.logger_reps_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag(LoggerTestTags.WARMUP_EDITOR_REPS)
                    )
                }
                if (editor.hasWeight) {
                    OutlinedTextField(
                        value = editor.weightInput,
                        onValueChange = onWeightChanged,
                        label = {
                            Text(stringResource(Res.string.logger_weight_label, weightUnit.label))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.testTag(LoggerTestTags.WARMUP_EDITOR_WEIGHT)
                    )
                    if (editor.weightExceedsCeiling) {
                        Text(
                            text = stringResource(
                                Res.string.logger_warmup_guidance_editor_weight_exceeds,
                                loadWeightText(LoadKind.EXTERNAL, editor.ceilingKg, weightUnit)
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                TextButton(onClick = onReset) {
                    Text(stringResource(Res.string.logger_warmup_guidance_editor_reset))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = editor.canSave,
                modifier = Modifier.testTag(LoggerTestTags.WARMUP_EDITOR_SAVE)
            ) {
                Text(stringResource(Res.string.logger_note_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(LoggerTestTags.WARMUP_EDITOR_CANCEL)
            ) {
                Text(stringResource(Res.string.logger_cancel))
            }
        }
    )
}

/** The localized label for a suggested preparation action. */
private fun WarmupAction.labelResource(): StringResource = when (this) {
    WarmupAction.EASY_GENERAL_MOVEMENT -> Res.string.logger_warmup_action_easy_general
    WarmupAction.LOWER_BODY_PRACTICE -> Res.string.logger_warmup_action_lower_body
    WarmupAction.SHOULDER_CIRCLES -> Res.string.logger_warmup_action_shoulder_circles
    WarmupAction.LIGHT_PUSH_PRACTICE -> Res.string.logger_warmup_action_light_push
    WarmupAction.LIGHT_PULL_PRACTICE -> Res.string.logger_warmup_action_light_pull
}

/**
 * The ordered guided view of the active workout: each prescribed exercise with its frozen target
 * and recorded progress. One set is confirmed per action; the remaining sets stay pending. A
 * completed exercise collapses to its summary until the user expands it, and a superset shows one
 * control set on the member whose turn it is.
 */
@Composable
private fun GuidedWorkoutSection(
    guided: GuidedWorkoutProgress,
    weightUnit: WeightUnit,
    exerciseLoadBasis: Map<String, LoadBasis>,
    writeFailed: Boolean,
    writeInProgress: Boolean,
    restTimer: RestTimerState?,
    restDurationSeconds: String,
    restDurationIsOverride: Boolean,
    restDurationIsPairRest: Boolean,
    restDurationIsSupersetTransition: Boolean,
    onRestDurationChanged: (String) -> Unit,
    onResetRestDuration: () -> Unit,
    onCancelRestTimer: () -> Unit,
    onLogSet: (Long) -> Unit,
    onLogSetNow: (Long) -> Unit,
    startedSetEntryId: Long?,
    frozenEntryIds: Set<Long>,
    onStartSet: (Long) -> Unit,
    onEditSet: (Long) -> Unit,
    onReplace: (Long) -> Unit,
    onInstructions: (Long) -> Unit,
    onLoadingCalculator: (GuidedExerciseProgress) -> Unit,
    expandedEntryIds: Set<Long>,
    actionEntryIds: Set<Long>,
    supersetControlEntryIds: Set<Long>,
    onToggleExpanded: (Long) -> Unit,
    nextEntryId: Long?,
    skippedEntryId: Long?,
    onSkipTurn: (Long) -> Unit,
    onResumeTurn: () -> Unit,
    onUnpair: (Long) -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(Res.string.logger_guided_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        Res.string.logger_active_progress,
                        guided.performedSets,
                        guided.prescribedSets
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (writeFailed) {
                Text(
                    text = stringResource(Res.string.logger_guided_set_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            restTimer?.let { timer ->
                Text(
                    text = if (timer.isFinished) {
                        stringResource(Res.string.logger_rest_timer_finished)
                    } else {
                        val seconds = (timer.remainingMillis + 999L) / 1_000L
                        stringResource(
                            Res.string.logger_rest_timer_remaining,
                            seconds / 60L,
                            seconds % 60L
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when {
                        restDurationIsSupersetTransition ->
                            stringResource(Res.string.logger_rest_timer_transition_note)
                        restDurationIsPairRest ->
                            stringResource(Res.string.logger_rest_timer_pair_note)
                        else -> stringResource(Res.string.logger_rest_timer_note)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                val supersetRest = restDurationIsPairRest || restDurationIsSupersetTransition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = restDurationSeconds,
                        onValueChange = onRestDurationChanged,
                        label = {
                            Text(
                                stringResource(
                                    when {
                                        restDurationIsSupersetTransition ->
                                            Res.string.logger_rest_timer_transition_duration
                                        restDurationIsPairRest ->
                                            Res.string.logger_rest_timer_pair_duration
                                        else -> Res.string.logger_rest_timer_duration
                                    }
                                )
                            )
                        },
                        singleLine = true,
                        readOnly = supersetRest,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancelRestTimer) {
                        Text(stringResource(Res.string.logger_rest_timer_cancel))
                    }
                }
                if (restDurationIsOverride && !supersetRest) {
                    TextButton(onClick = onResetRestDuration) {
                        Text(stringResource(Res.string.logger_rest_timer_use_default))
                    }
                }
            }
            if (guided.allComplete) {
                Text(
                    text = stringResource(Res.string.logger_guided_all_done),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            guided.exercises.forEach { exercise ->
                // A complete exercise collapses to its name and completion summary until the user
                // expands it; unfinished work always keeps its rows visible.
                val collapsed = exercise.isComplete &&
                    exercise.occurrenceEntryId !in expandedEntryIds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exercise.exerciseName)
                        if (showsTechniqueConsiderations(exercise.exerciseId)) {
                            Text(
                                text = stringResource(Res.string.logger_technique_considerations),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(
                            onClick = { onInstructions(exercise.occurrenceEntryId) },
                            modifier = Modifier.testTag(
                                LoggerTestTags.guidedInstructions(exercise.occurrenceEntryId)
                            )
                        ) {
                            Text(stringResource(Res.string.logger_instructions))
                        }
                        Text(
                            text = stringResource(
                                Res.string.logger_active_progress,
                                exercise.performedWorkingSets.coerceAtMost(
                                    exercise.prescribedSets
                                ),
                                exercise.prescribedSets
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (collapsed) {
                            TextButton(
                                onClick = { onToggleExpanded(exercise.occurrenceEntryId) }
                            ) {
                                Text(stringResource(Res.string.logger_guided_expand_sets))
                            }
                        } else {
                            // A slot that already holds recorded work, or the slot currently started,
                            // cannot be swapped: hide the action instead of offering a refused one.
                            if (exercise.occurrenceEntryId !in frozenEntryIds &&
                                exercise.occurrenceEntryId != startedSetEntryId
                            ) {
                                TextButton(
                                    onClick = { onReplace(exercise.occurrenceEntryId) },
                                    enabled = !writeInProgress
                                ) {
                                    Text(stringResource(Res.string.logger_replace))
                                }
                            }
                            val targetSeconds = exercise.durationSeconds
                            val loadText = loadWeightText(
                                exercise.loadKind,
                                exercise.weightKg,
                                weightUnit,
                                exerciseLoadBasis[exercise.exerciseId]
                            )
                            Text(
                                text = if (targetSeconds != null) {
                                    stringResource(
                                        Res.string.logger_guided_target_timed,
                                        targetSeconds,
                                        loadText
                                    )
                                } else {
                                    stringResource(
                                        Res.string.logger_guided_target,
                                        exercise.reps ?: 0,
                                        loadText
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (exercise.loadKind == LoadKind.EXTERNAL &&
                                exercise.weightKg == null
                            ) {
                                noLoadReasonText(exercise.explanation)?.let { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (loggerLoadingTargetEligible(exercise.loadKind, exercise.weightKg)) {
                                TextButton(
                                    onClick = { onLoadingCalculator(exercise) },
                                    modifier = Modifier.testTag(
                                        LoggerLoadingCalculatorTestTags.guidedAction(
                                            exercise.occurrenceEntryId
                                        )
                                    )
                                ) {
                                    Text(
                                        stringResource(
                                            Res.string.logger_loading_calculator_action
                                        )
                                    )
                                }
                            }
                            if (startedSetEntryId == exercise.occurrenceEntryId) {
                                Text(
                                    text = stringResource(Res.string.logger_guided_set_started),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            exercise.superset?.let { group ->
                                Text(
                                    text = stringResource(
                                        Res.string.logger_superset_pair_note,
                                        group.transitionSeconds,
                                        group.pairRestSeconds
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (exercise.occurrenceEntryId == nextEntryId) {
                                    Text(
                                        text = stringResource(Res.string.logger_superset_next),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (exercise.occurrenceEntryId in supersetControlEntryIds) {
                                    // The pair owns one control set. Resume appears here when any
                                    // member is skipped, so it stays reachable from the active one.
                                    val pairSkipped = skippedEntryId != null &&
                                        guided.exercises.any {
                                            it.occurrenceEntryId == skippedEntryId &&
                                                it.superset?.key == group.key
                                        }
                                    Row {
                                        if (pairSkipped) {
                                            TextButton(onClick = onResumeTurn) {
                                                Text(
                                                    stringResource(
                                                        Res.string.logger_superset_resume_turn
                                                    )
                                                )
                                            }
                                        } else {
                                            TextButton(
                                                onClick = {
                                                    onSkipTurn(exercise.occurrenceEntryId)
                                                },
                                                enabled = !writeInProgress
                                            ) {
                                                Text(
                                                    stringResource(
                                                        Res.string.logger_superset_skip_turn
                                                    )
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = { onUnpair(exercise.occurrenceEntryId) },
                                            enabled = !writeInProgress
                                        ) {
                                            Text(stringResource(Res.string.logger_superset_unpair))
                                        }
                                    }
                                }
                            }
                            if (exercise.isComplete) {
                                TextButton(
                                    onClick = { onToggleExpanded(exercise.occurrenceEntryId) }
                                ) {
                                    Text(stringResource(Res.string.logger_guided_collapse_sets))
                                }
                            }
                        }
                        TargetExplanationDisclosure(
                            key = exercise.occurrenceEntryId,
                            explanation = exercise.explanation,
                            weightUnit = weightUnit
                        )
                    }
                    // Only the paired member whose turn it is gets set controls, so the same round
                    // cannot be logged twice from the group. A single exercise keeps its controls.
                    if (exercise.occurrenceEntryId in actionEntryIds) {
                        Column {
                            val isStartedSlot = startedSetEntryId == exercise.occurrenceEntryId
                            TextButton(
                                onClick = { onStartSet(exercise.occurrenceEntryId) },
                                enabled = !writeInProgress && !isStartedSlot
                            ) {
                                Text(stringResource(Res.string.logger_guided_start_set))
                            }
                            TextButton(
                                // A live start owns the completion: an ordinary log is only
                                // available when no guided set is started.
                                onClick = { onLogSet(exercise.occurrenceEntryId) },
                                enabled = !writeInProgress && startedSetEntryId == null
                            ) {
                                Text(stringResource(Res.string.logger_log_button))
                            }
                            TextButton(
                                onClick = { onLogSetNow(exercise.occurrenceEntryId) },
                                enabled = !writeInProgress && isStartedSlot
                            ) {
                                Text(stringResource(Res.string.logger_guided_log_completed_now))
                            }
                            TextButton(
                                onClick = { onEditSet(exercise.occurrenceEntryId) },
                                enabled = !writeInProgress
                            ) {
                                Text(stringResource(Res.string.logger_draft_edit))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StationSelector(
    stations: List<EquipmentStation>,
    selectedStationId: Long?,
    onStationSelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = stations.firstOrNull { it.id == selectedStationId }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selected?.let {
                    stringResource(Res.string.logger_station_attributed, it.name)
                } ?: stringResource(Res.string.logger_station_choose)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.logger_station_none)) },
                onClick = {
                    expanded = false
                    onStationSelected(null)
                }
            )
            stations.forEach { station ->
                DropdownMenuItem(
                    text = { Text(station.name) },
                    onClick = {
                        expanded = false
                        onStationSelected(station.id)
                    }
                )
            }
        }
    }
}
