package com.hydrafit.app.feature.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_change_exercise
import hydrafit.feature.logger.generated.resources.logger_history_back
import hydrafit.feature.logger.generated.resources.logger_search_label
import hydrafit.feature.logger.generated.resources.logger_select_exercise
import org.jetbrains.compose.resources.stringResource

/** The exercise picker is its own destination so the form never has to scroll to reach it. */
@Composable
internal fun ExercisePickerScreen(
    state: WorkoutLoggerUiState,
    onBack: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onExerciseSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "picker_header") {
            LoggerPageHeader(
                title = stringResource(Res.string.logger_select_exercise),
                onBack = onBack
            )
        }
        item(key = "picker_search") {
            OutlinedTextField(
                value = state.exerciseSearch,
                onValueChange = onSearchChanged,
                label = { Text(stringResource(Res.string.logger_search_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
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

/** The title plus the explicit way back that every Logger child page carries. */
@Composable
internal fun LoggerPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        TextButton(onClick = onBack) {
            Text(stringResource(Res.string.logger_history_back))
        }
    }
}

/** The one exercise control: an unset prompt, or the chosen name with a change action. */
@Composable
internal fun SelectedExerciseRow(selectedName: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedName ?: stringResource(Res.string.logger_select_exercise),
                    style = MaterialTheme.typography.titleMedium
                )
                if (selectedName != null) {
                    Text(
                        text = stringResource(Res.string.logger_change_exercise),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
