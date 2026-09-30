package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.focus_full_body
import hydrafit.feature.logger.generated.resources.focus_legs
import hydrafit.feature.logger.generated.resources.focus_lower
import hydrafit.feature.logger.generated.resources.focus_pull
import hydrafit.feature.logger.generated.resources.focus_push
import hydrafit.feature.logger.generated.resources.focus_upper
import hydrafit.feature.logger.generated.resources.logger_add_weight
import hydrafit.feature.logger.generated.resources.logger_delete_set
import hydrafit.feature.logger.generated.resources.logger_log_button
import hydrafit.feature.logger.generated.resources.logger_recent
import hydrafit.feature.logger.generated.resources.logger_reps_label
import hydrafit.feature.logger.generated.resources.logger_title
import hydrafit.feature.logger.generated.resources.logger_today
import hydrafit.feature.logger.generated.resources.logger_warmup
import hydrafit.feature.logger.generated.resources.logger_warmup_suffix
import hydrafit.feature.logger.generated.resources.logger_weight_label
import hydrafit.feature.logger.generated.resources.logger_weight_none
import hydrafit.feature.logger.generated.resources.nav_label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val loggerRoute: String = "log"

val loggerDestination: FeatureDestination = FeatureDestination(
    route = loggerRoute,
    label = Res.string.nav_label,
    graph = { loggerGraph() }
)

fun NavGraphBuilder.loggerGraph() {
    composable(loggerRoute) { WorkoutLoggerRoute() }
}

@Composable
fun WorkoutLoggerRoute(
    modifier: Modifier = Modifier,
    viewModel: WorkoutLoggerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutLoggerScreen(
        state = state,
        onExerciseSelected = viewModel::onExerciseSelected,
        onRepsChanged = viewModel::onRepsChanged,
        onWeightChanged = viewModel::onWeightChanged,
        onWarmupToggled = viewModel::onWarmupToggled,
        onLog = viewModel::log,
        onDeleteSet = viewModel::deleteSet,
        onRevealWeight = viewModel::onRevealWeight,
        modifier = modifier
    )
}

@Composable
fun WorkoutLoggerScreen(
    state: WorkoutLoggerUiState,
    onExerciseSelected: (String) -> Unit,
    onRepsChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onWarmupToggled: (Boolean) -> Unit,
    onLog: () -> Unit,
    onDeleteSet: (Long) -> Unit,
    onRevealWeight: () -> Unit,
    modifier: Modifier = Modifier
) {
    // One scroll container for the whole screen: a fixed-height picker inside a non-scrolling
    // parent used to push the form and the recent-sets list off short viewports.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(Res.string.logger_title),
                style = MaterialTheme.typography.headlineSmall
            )
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(state.exercises, key = { it.id }) { exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
        item {
            OutlinedTextField(
                value = state.reps,
                onValueChange = onRepsChanged,
                label = { Text(stringResource(Res.string.logger_reps_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                TextButton(onClick = onRevealWeight) {
                    Text(stringResource(Res.string.logger_add_weight))
                }
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
            Button(onClick = onLog, enabled = state.canLog) {
                Text(stringResource(Res.string.logger_log_button))
            }
        }
        item {
            Text(
                text = stringResource(Res.string.logger_recent),
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(state.recentSets) { row ->
            val unit = state.weightUnit
            val weight = row.weightKg
                ?.let { kg ->
                    formatWeight(unit.kilogramsToDisplay(kg)) + " " + unit.label
                }
                ?: stringResource(Res.string.logger_weight_none)
            val warmupSuffix = if (row.isWarmup) {
                " " + stringResource(Res.string.logger_warmup_suffix)
            } else {
                ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${row.exerciseName}  ${row.reps} x $weight$warmupSuffix",
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onDeleteSet(row.id) }) {
                    Text(stringResource(Res.string.logger_delete_set))
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
