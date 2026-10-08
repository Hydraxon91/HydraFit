package com.hydrafit.app.feature.splitbuilder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmCoverageUnmetReason
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SwapCandidate
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
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
import hydrafit.feature.splitbuilder.generated.resources.split_error_quota_exhausted
import hydrafit.feature.splitbuilder.generated.resources.split_error_rate_limited
import hydrafit.feature.splitbuilder.generated.resources.split_error_service_unavailable
import hydrafit.feature.splitbuilder.generated.resources.split_error_timeout
import hydrafit.feature.splitbuilder.generated.resources.split_error_transient
import hydrafit.feature.splitbuilder.generated.resources.split_exercise_line
import hydrafit.feature.splitbuilder.generated.resources.split_fallback_invalid_response
import hydrafit.feature.splitbuilder.generated.resources.split_fallback_note
import hydrafit.feature.splitbuilder.generated.resources.split_generated_by
import hydrafit.feature.splitbuilder.generated.resources.split_generated_routine_name
import hydrafit.feature.splitbuilder.generated.resources.split_history
import hydrafit.feature.splitbuilder.generated.resources.split_history_entry
import hydrafit.feature.splitbuilder.generated.resources.split_loading
import hydrafit.feature.splitbuilder.generated.resources.split_loading_progress
import hydrafit.feature.splitbuilder.generated.resources.split_muscle_biceps
import hydrafit.feature.splitbuilder.generated.resources.split_muscle_triceps
import hydrafit.feature.splitbuilder.generated.resources.split_plan_accepted
import hydrafit.feature.splitbuilder.generated.resources.split_regenerate
import hydrafit.feature.splitbuilder.generated.resources.split_retry
import hydrafit.feature.splitbuilder.generated.resources.split_routine_saved
import hydrafit.feature.splitbuilder.generated.resources.split_routine_saved_view
import hydrafit.feature.splitbuilder.generated.resources.split_save_routine
import hydrafit.feature.splitbuilder.generated.resources.split_schedule_choose_date
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
    viewModel: SplitBuilderViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            onAcceptPlan = viewModel::onAcceptPlan,
            onSchedulePlan = viewModel::onScheduleRequested,
            onSaveAsRoutine = {
                viewModel.onSaveAsRoutine(
                    "$generatedRoutineName " + isoLocalDateTime(
                        timeProvider.nowMillis(),
                        timeProvider.utcOffsetMillis()
                    )
                )
            },
            onRegenerate = viewModel::refresh,
            onViewAcceptedPlan = viewModel::onViewAcceptedPlan,
            onDeletePlan = viewModel::onDeletePlan,
            onSwapRequested = viewModel::onSwapRequested,
            onSwapCandidateSelected = viewModel::onSwapCandidateSelected,
            onSwapDialogDismissed = viewModel::onSwapDialogDismissed,
            onRetry = viewModel::refresh,
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
}

@Composable
fun SplitBuilderScreen(
    state: SplitBuilderUiState,
    weightUnit: WeightUnit,
    planProgress: OnDevicePlanProgress?,
    onDaysPerWeekSelected: (Int) -> Unit,
    onSetsPerExerciseChanged: (Int) -> Unit,
    onAccessorySetsPerExerciseChanged: (Int) -> Unit,
    onAcceptPlan: () -> Unit,
    onSchedulePlan: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onRegenerate: () -> Unit,
    onViewAcceptedPlan: (AcceptedPlan) -> Unit,
    onDeletePlan: (AcceptedPlan) -> Unit,
    onSwapRequested: (Int, Int) -> Unit,
    onSwapCandidateSelected: (String) -> Unit,
    onSwapDialogDismissed: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
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
                Button(onClick = onAcceptPlan) {
                    Text(stringResource(Res.string.split_accept_plan))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSchedulePlan) {
                    Text(stringResource(Res.string.split_start_block))
                }
                TextButton(onClick = onSaveAsRoutine) {
                    Text(stringResource(Res.string.split_save_routine))
                }
            }
            Button(onClick = onRegenerate, enabled = state.canRegenerate) {
                Text(stringResource(Res.string.split_regenerate))
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
                        val name = state.exerciseNames[exercise.exerciseId] ?: exercise.exerciseId
                        val weight = exercise.suggestedWeightKg?.let { kg ->
                            stringResource(
                                Res.string.split_suggested_weight,
                                formatWeight(weightUnit.kilogramsToDisplay(kg)),
                                weightUnit.label
                            )
                        }.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    Res.string.split_exercise_line,
                                    name,
                                    exercise.sets,
                                    exercise.reps,
                                    weight
                                ),
                                modifier = Modifier.weight(1f)
                            )
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

private fun ArmCoverageUnmetReason.labelResource(): StringResource = when (this) {
    ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE ->
        Res.string.split_volume_unmet_candidate
    ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE ->
        Res.string.split_volume_unmet_fatigue
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
