package com.hydrafit.app.feature.splitbuilder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.SplitFocus
import hydrafit.feature.splitbuilder.generated.resources.Res
import hydrafit.feature.splitbuilder.generated.resources.focus_full_body
import hydrafit.feature.splitbuilder.generated.resources.focus_legs
import hydrafit.feature.splitbuilder.generated.resources.focus_lower
import hydrafit.feature.splitbuilder.generated.resources.focus_pull
import hydrafit.feature.splitbuilder.generated.resources.focus_push
import hydrafit.feature.splitbuilder.generated.resources.focus_upper
import hydrafit.feature.splitbuilder.generated.resources.split_builder_title
import hydrafit.feature.splitbuilder.generated.resources.split_day
import hydrafit.feature.splitbuilder.generated.resources.split_days_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val splitBuilderRoute: String = "plan"

private val dayOptions = 2..6

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
        modifier = modifier
    )
}

@Composable
fun SplitBuilderScreen(
    state: SplitBuilderUiState,
    onDaysPerWeekSelected: (Int) -> Unit,
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
        state.plan?.days?.forEach { day ->
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

private fun SplitFocus.labelResource(): StringResource = when (this) {
    SplitFocus.PUSH -> Res.string.focus_push
    SplitFocus.PULL -> Res.string.focus_pull
    SplitFocus.LEGS -> Res.string.focus_legs
    SplitFocus.UPPER -> Res.string.focus_upper
    SplitFocus.LOWER -> Res.string.focus_lower
    SplitFocus.FULL_BODY -> Res.string.focus_full_body
}
