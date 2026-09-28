package com.hydrafit.app.feature.splitbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.navigation.FeatureDestination
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
import hydrafit.feature.splitbuilder.generated.resources.split_builder_title
import hydrafit.feature.splitbuilder.generated.resources.split_day
import hydrafit.feature.splitbuilder.generated.resources.split_days_label
import hydrafit.feature.splitbuilder.generated.resources.split_error
import hydrafit.feature.splitbuilder.generated.resources.split_error_transient
import hydrafit.feature.splitbuilder.generated.resources.split_fallback_note
import hydrafit.feature.splitbuilder.generated.resources.split_generated_by
import hydrafit.feature.splitbuilder.generated.resources.split_loading
import hydrafit.feature.splitbuilder.generated.resources.split_retry
import hydrafit.feature.splitbuilder.generated.resources.split_sets_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val splitBuilderRoute: String = "plan"

val splitBuilderDestination: FeatureDestination = FeatureDestination(
    route = splitBuilderRoute,
    label = Res.string.nav_label,
    graph = { splitBuilderGraph() }
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
    SplitBuilderScreen(
        state = state,
        onDaysPerWeekSelected = viewModel::onDaysPerWeekSelected,
        onSetsPerExerciseChanged = viewModel::onSetsPerExerciseChanged,
        onRetry = viewModel::refresh,
        modifier = modifier
    )
}

@Composable
fun SplitBuilderScreen(
    state: SplitBuilderUiState,
    onDaysPerWeekSelected: (Int) -> Unit,
    onSetsPerExerciseChanged: (Int) -> Unit,
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
        if (state.isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(text = stringResource(Res.string.split_loading))
            }
        }
        if (state.hasError) {
            val errorText = if (state.isTransientError) {
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
                    Res.string.split_generated_by,
                    stringResource(plan.engine.labelResource())
                ),
                style = MaterialTheme.typography.labelLarge
            )
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
            }
            plan.days.forEach { day ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.split_day, day.dayIndex + 1) +
                            " - " + stringResource(day.focus.labelResource()),
                        style = MaterialTheme.typography.titleMedium
                    )
                    day.exercises.forEach { exercise ->
                        val name = state.exerciseNames[exercise.exerciseId] ?: exercise.exerciseId
                        Text(text = "$name  ${exercise.sets} x ${exercise.reps}")
                    }
                }
            }
        }
    }
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
