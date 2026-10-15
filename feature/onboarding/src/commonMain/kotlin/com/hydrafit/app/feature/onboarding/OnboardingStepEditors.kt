package com.hydrafit.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.unit.formatWeight
import com.hydrafit.app.core.navigation.AppTab
import hydrafit.feature.onboarding.generated.resources.Res
import hydrafit.feature.onboarding.generated.resources.onboarding_build_routine
import hydrafit.feature.onboarding.generated.resources.onboarding_day_friday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_monday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_saturday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_sunday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_thursday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_tuesday
import hydrafit.feature.onboarding.generated.resources.onboarding_day_wednesday
import hydrafit.feature.onboarding.generated.resources.onboarding_equipment_empty
import hydrafit.feature.onboarding.generated.resources.onboarding_equipment_profile
import hydrafit.feature.onboarding.generated.resources.onboarding_equipment_profile_note
import hydrafit.feature.onboarding.generated.resources.onboarding_equipment_prompt
import hydrafit.feature.onboarding.generated.resources.onboarding_first_workout_intro
import hydrafit.feature.onboarding.generated.resources.onboarding_generate_plan
import hydrafit.feature.onboarding.generated.resources.onboarding_goal_balanced
import hydrafit.feature.onboarding.generated.resources.onboarding_goal_endurance
import hydrafit.feature.onboarding.generated.resources.onboarding_goal_hypertrophy
import hydrafit.feature.onboarding.generated.resources.onboarding_goal_prompt
import hydrafit.feature.onboarding.generated.resources.onboarding_goal_strength
import hydrafit.feature.onboarding.generated.resources.onboarding_log_without_plan
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_no_match
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_none
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_prompt
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_reps
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_save
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_search
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_set
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_unknown
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_value
import hydrafit.feature.onboarding.generated.resources.onboarding_pr_weight
import hydrafit.feature.onboarding.generated.resources.onboarding_unit_kg
import hydrafit.feature.onboarding.generated.resources.onboarding_unit_lb
import hydrafit.feature.onboarding.generated.resources.onboarding_units_prompt
import hydrafit.feature.onboarding.generated.resources.onboarding_weekdays_decide_later
import hydrafit.feature.onboarding.generated.resources.onboarding_weekdays_hint
import hydrafit.feature.onboarding.generated.resources.onboarding_weekdays_prompt
import hydrafit.feature.onboarding.generated.resources.onboarding_welcome_intro
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The per-step editors. Each one renders the draft the ViewModel already loaded and reports a change
 * through the `edit`/`editPersonalRecordEntry` API — a "Save best set" click is reported to the
 * ViewModel, which stages it through the same rule Next uses; none of them write to storage, and every
 * control is disabled while a save is in flight.
 */
@Composable
internal fun OnboardingStepEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit,
    onSavePersonalRecordEntry: () -> Unit,
    onOpenPersonalRecordEntry: (String) -> Unit,
    onEditPersonalRecordEntry: (String, String) -> Unit,
    onDismissPersonalRecordEntry: () -> Unit,
    onHandoff: (AppTab) -> Unit
) {
    when (state.step) {
        SetupStep.WELCOME -> WelcomeEditor()
        SetupStep.UNITS -> UnitsEditor(state, enabled, onEdit)
        SetupStep.EQUIPMENT -> EquipmentEditor(state, enabled, onEdit)
        SetupStep.GOAL -> GoalEditor(state, enabled, onEdit)
        SetupStep.WEEKDAYS -> WeekdaysEditor(state, enabled, onEdit)
        SetupStep.PERSONAL_RECORDS -> PersonalRecordsEditor(
            state = state,
            enabled = enabled,
            onEdit = onEdit,
            onSavePersonalRecordEntry = onSavePersonalRecordEntry,
            onOpenPersonalRecordEntry = onOpenPersonalRecordEntry,
            onEditPersonalRecordEntry = onEditPersonalRecordEntry,
            onDismissPersonalRecordEntry = onDismissPersonalRecordEntry
        )
        SetupStep.FIRST_WORKOUT -> FirstWorkoutEditor(enabled, onHandoff)
    }
}

@Composable
private fun WelcomeEditor() {
    Text(
        text = stringResource(Res.string.onboarding_welcome_intro),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun UnitsEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit
) {
    val selected = (state.input as? SetupStepInput.Units)?.unit
    val options = listOf(
        WeightUnit.KG to Res.string.onboarding_unit_kg,
        WeightUnit.LB to Res.string.onboarding_unit_lb
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.UNITS)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_units_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        options.forEach { (unit, label) ->
            RadioChoiceRow(
                label = stringResource(label),
                selected = selected == unit,
                enabled = enabled,
                onSelect = { onEdit(SetupStepInput.Units(unit)) }
            )
        }
    }
}

@Composable
private fun EquipmentEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit
) {
    val selected = (state.input as? SetupStepInput.Equipment)?.selected.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.EQUIPMENT),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_equipment_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        // The selection below is written to the active profile, so the step names it instead of
        // letting the user assume a global choice.
        state.equipmentProfileName?.let { profileName ->
            Text(
                text = stringResource(Res.string.onboarding_equipment_profile, profileName),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(Res.string.onboarding_equipment_profile_note),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.equipment.isEmpty()) {
            Text(
                text = stringResource(Res.string.onboarding_equipment_empty),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            state.equipment.forEach { item ->
                val tag: EquipmentTag = item.id
                // Selection only: this step never edits a max weight, so ceilings stay untouched.
                CheckChoiceRow(
                    label = item.name,
                    checked = tag in selected,
                    enabled = enabled,
                    onToggle = {
                        val next = if (tag in selected) selected - tag else selected + tag
                        val input = state.input as SetupStepInput.Equipment
                        onEdit(input.copy(selected = next))
                    }
                )
            }
        }
    }
}

@Composable
private fun GoalEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit
) {
    // All four goals are offered and the loaded one is preselected, so a stored default such as
    // BALANCED is always visible in the group instead of being kept invisibly by Next.
    val current = selectedGoalChoice(state.input)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.GOAL)
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_goal_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        onboardingGoalChoices.forEach { goal ->
            RadioChoiceRow(
                label = stringResource(goal.labelResource()),
                selected = current == goal,
                enabled = enabled,
                onSelect = { onEdit(SetupStepInput.Goal(goal)) }
            )
        }
    }
}

private fun TrainingGoal.labelResource(): StringResource = when (this) {
    TrainingGoal.BALANCED -> Res.string.onboarding_goal_balanced
    TrainingGoal.STRENGTH -> Res.string.onboarding_goal_strength
    TrainingGoal.HYPERTROPHY -> Res.string.onboarding_goal_hypertrophy
    TrainingGoal.ENDURANCE -> Res.string.onboarding_goal_endurance
}

@Composable
private fun WeekdaysEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit
) {
    val selected = (state.input as? SetupStepInput.Weekdays)?.weekdays
    val days = listOf(
        DayOfWeek.MONDAY to Res.string.onboarding_day_monday,
        DayOfWeek.TUESDAY to Res.string.onboarding_day_tuesday,
        DayOfWeek.WEDNESDAY to Res.string.onboarding_day_wednesday,
        DayOfWeek.THURSDAY to Res.string.onboarding_day_thursday,
        DayOfWeek.FRIDAY to Res.string.onboarding_day_friday,
        DayOfWeek.SATURDAY to Res.string.onboarding_day_saturday,
        DayOfWeek.SUNDAY to Res.string.onboarding_day_sunday
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.WEEKDAYS),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_weekdays_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        days.forEach { (day, label) ->
            CheckChoiceRow(
                label = stringResource(label),
                checked = day in selected.orEmpty(),
                enabled = enabled,
                onToggle = { onEdit(SetupStepInput.Weekdays(toggleWeekday(selected, day))) }
            )
        }
        // "Decide later" is the single "no days chosen" alternative, not another day to toggle, so it
        // uses the single-choice role and stays outside the day checkbox list.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected == null,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onEdit(SetupStepInput.Weekdays(null)) }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected == null, onClick = null, enabled = enabled)
            Text(
                text = stringResource(Res.string.onboarding_weekdays_decide_later),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = stringResource(Res.string.onboarding_weekdays_hint),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PersonalRecordsEditor(
    state: OnboardingUiState,
    enabled: Boolean,
    onEdit: (SetupStepInput) -> Unit,
    onSavePersonalRecordEntry: () -> Unit,
    onOpenPersonalRecordEntry: (String) -> Unit,
    onEditPersonalRecordEntry: (String, String) -> Unit,
    onDismissPersonalRecordEntry: () -> Unit
) {
    val draft = (state.input as? SetupStepInput.PersonalRecords)?.edits.orEmpty()
    var query by remember { mutableStateOf("") }
    val openEntry = state.personalRecordEntry
    val matches = state.eligibleExercises.filter { matchesExerciseNameQuery(it.name, query) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OnboardingTestTags.PERSONAL_RECORDS),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_pr_prompt),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(Res.string.onboarding_pr_search)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (matches.isEmpty()) {
            Text(
                text = stringResource(Res.string.onboarding_pr_no_match),
                style = MaterialTheme.typography.bodySmall
            )
        }
        matches.forEach { exercise ->
            val record = displayedRecord(exercise.id, draft, state.existingRecords)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = personalRecordLabel(state.entryUnit, record),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(
                    onClick = { onOpenPersonalRecordEntry(exercise.id) },
                    enabled = enabled
                ) {
                    Text(stringResource(Res.string.onboarding_pr_set))
                }
                if (openEntry?.exerciseId == exercise.id) {
                    PersonalRecordEntry(
                        entry = openEntry,
                        enabled = enabled,
                        onEdit = onEditPersonalRecordEntry,
                        onSave = onSavePersonalRecordEntry,
                        // Records nothing: it only closes this entry without deleting the record.
                        onUnknown = onDismissPersonalRecordEntry
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordEntry(
    entry: PersonalRecordEntryDraft,
    enabled: Boolean,
    onEdit: (String, String) -> Unit,
    onSave: () -> Unit,
    onUnknown: () -> Unit
) {
    val weight = parseDisplayWeight(entry.weightText)
    val reps = parseReps(entry.repsText)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = entry.weightText,
                onValueChange = { onEdit(it, entry.repsText) },
                label = { Text(stringResource(Res.string.onboarding_pr_weight)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(140.dp)
            )
            OutlinedTextField(
                value = entry.repsText,
                onValueChange = { onEdit(entry.weightText, it) },
                label = { Text(stringResource(Res.string.onboarding_pr_reps)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp)
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The button stays gated on a parseable pair, but it stages the entry's own text through
            // the ViewModel, so Save cannot record a different value than Next would.
            Button(
                onClick = onSave,
                enabled = enabled && weight != null && reps != null
            ) {
                Text(stringResource(Res.string.onboarding_pr_save))
            }
            TextButton(onClick = onUnknown, enabled = enabled) {
                Text(stringResource(Res.string.onboarding_pr_unknown))
            }
        }
    }
}

@Composable
private fun personalRecordLabel(unit: WeightUnit, record: PersonalRecord?): String =
    if (record == null) {
        stringResource(Res.string.onboarding_pr_none)
    } else {
        stringResource(
            Res.string.onboarding_pr_value,
            formatWeight(unit.kilogramsToDisplay(record.weightKg)),
            unit.label,
            record.reps.toString()
        )
    }

@Composable
private fun FirstWorkoutEditor(enabled: Boolean, onHandoff: (AppTab) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.onboarding_first_workout_intro),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = { onHandoff(AppTab.PLAN) },
            enabled = enabled,
            modifier = Modifier.testTag(OnboardingTestTags.HANDOFF_GENERATE_PLAN)
        ) {
            Text(stringResource(Res.string.onboarding_generate_plan))
        }
        Button(
            onClick = { onHandoff(AppTab.ROUTINES) },
            enabled = enabled,
            modifier = Modifier.testTag(OnboardingTestTags.HANDOFF_BUILD_ROUTINE)
        ) {
            Text(stringResource(Res.string.onboarding_build_routine))
        }
        Button(
            onClick = { onHandoff(AppTab.LOG) },
            enabled = enabled,
            modifier = Modifier.testTag(OnboardingTestTags.HANDOFF_LOG_WITHOUT_PLAN)
        ) {
            Text(stringResource(Res.string.onboarding_log_without_plan))
        }
    }
}

/** One single-choice row: the whole row is the target, and the graphic carries the state. */
@Composable
private fun RadioChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One multi-select row; the checkbox graphic, not colour alone, shows its state. */
@Composable
private fun CheckChoiceRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
