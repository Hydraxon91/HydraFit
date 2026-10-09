package com.hydrafit.app.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExerciseProfile
import hydrafit.feature.equipment.generated.resources.Res
import hydrafit.feature.equipment.generated.resources.equipment_cancel
import hydrafit.feature.equipment.generated.resources.equipment_equipment_section
import hydrafit.feature.equipment.generated.resources.equipment_load_bodyweight
import hydrafit.feature.equipment.generated.resources.equipment_load_bodyweight_added
import hydrafit.feature.equipment.generated.resources.equipment_load_external
import hydrafit.feature.equipment.generated.resources.equipment_load_section
import hydrafit.feature.equipment.generated.resources.equipment_load_unspecified
import hydrafit.feature.equipment.generated.resources.equipment_movement_pattern
import hydrafit.feature.equipment.generated.resources.equipment_muscles
import hydrafit.feature.equipment.generated.resources.equipment_profile_apply
import hydrafit.feature.equipment.generated.resources.equipment_profile_canonical
import hydrafit.feature.equipment.generated.resources.equipment_profile_choose
import hydrafit.feature.equipment.generated.resources.equipment_profile_current
import hydrafit.feature.equipment.generated.resources.equipment_profile_identity
import hydrafit.feature.equipment.generated.resources.equipment_profile_no
import hydrafit.feature.equipment.generated.resources.equipment_profile_none
import hydrafit.feature.equipment.generated.resources.equipment_profile_note
import hydrafit.feature.equipment.generated.resources.equipment_profile_proposed
import hydrafit.feature.equipment.generated.resources.equipment_profile_title
import hydrafit.feature.equipment.generated.resources.equipment_profile_yes
import hydrafit.feature.equipment.generated.resources.equipment_unilateral
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** UI callbacks only; no new dependency or persistence path. */
data class ExerciseProfileSuggestionActions(
    val find: () -> Unit,
    val choose: (String) -> Unit,
    val toggle: (ExerciseProfileGroup) -> Unit,
    val apply: () -> Unit,
    val dismiss: () -> Unit
)

@Composable
internal fun ExerciseProfileSuggestionDialog(
    editor: ExerciseEditorState,
    equipment: List<Equipment>,
    actions: ExerciseProfileSuggestionActions
) {
    val suggestion = editor.suggestion
    val candidate = suggestion.preview
    AlertDialog(
        onDismissRequest = actions.dismiss,
        title = {
            Text(
                stringResource(
                    if (candidate == null) {
                        Res.string.equipment_profile_choose
                    } else {
                        Res.string.equipment_profile_title
                    }
                )
            )
        },
        confirmButton = {
            if (candidate != null) {
                Button(onClick = actions.apply, enabled = suggestion.selectedGroups.isNotEmpty()) {
                    Text(stringResource(Res.string.equipment_profile_apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = actions.dismiss) {
                Text(stringResource(Res.string.equipment_cancel))
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (candidate == null) {
                    suggestion.candidates.forEach { option ->
                        TextButton(onClick = { actions.choose(option.catalogId) }) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                CatalogProfileIdentity(option)
                                Text(
                                    profileGroupValue(
                                        ExerciseProfileGroup.EQUIPMENT,
                                        option.profile,
                                        equipment
                                    )
                                )
                            }
                        }
                    }
                } else {
                    CatalogProfileIdentity(candidate)
                    Text(
                        stringResource(Res.string.equipment_profile_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                    val current = ExerciseProfile(
                        editor.equipment,
                        editor.movementPattern,
                        editor.involvements,
                        editor.loadCapability,
                        editor.isUnilateral
                    )
                    ExerciseProfileGroup.entries.forEach { group ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = group in suggestion.selectedGroups,
                                onCheckedChange = { actions.toggle(group) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(group.labelResource()),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    stringResource(
                                        Res.string.equipment_profile_current,
                                        profileGroupValue(group, current, equipment)
                                    )
                                )
                                Text(
                                    stringResource(
                                        Res.string.equipment_profile_proposed,
                                        profileGroupValue(group, candidate.profile, equipment)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CatalogProfileIdentity(candidate: CatalogExerciseProfile) {
    Text(candidate.displayName, style = MaterialTheme.typography.titleSmall)
    if (candidate.displayName != candidate.canonicalName) {
        Text(stringResource(Res.string.equipment_profile_canonical, candidate.canonicalName))
    }
    Text(
        stringResource(Res.string.equipment_profile_identity, candidate.catalogId),
        style = MaterialTheme.typography.bodySmall
    )
}

private fun ExerciseProfileGroup.labelResource(): StringResource = when (this) {
    ExerciseProfileGroup.EQUIPMENT -> Res.string.equipment_equipment_section
    ExerciseProfileGroup.PATTERN -> Res.string.equipment_movement_pattern
    ExerciseProfileGroup.INVOLVEMENTS -> Res.string.equipment_muscles
    ExerciseProfileGroup.LOAD -> Res.string.equipment_load_section
    ExerciseProfileGroup.UNILATERAL -> Res.string.equipment_unilateral
}

@Composable
private fun profileGroupValue(
    group: ExerciseProfileGroup,
    profile: ExerciseProfile,
    equipment: List<Equipment>
): String = when (group) {
    ExerciseProfileGroup.EQUIPMENT -> profile.equipment.sortedBy { it.id }.joinToString { tag ->
        equipment.firstOrNull { it.id == tag }?.name ?: tag.displayName
    }.ifEmpty { stringResource(Res.string.equipment_profile_none) }
    ExerciseProfileGroup.PATTERN -> stringResource(profile.movementPattern.labelResource())
    ExerciseProfileGroup.INVOLVEMENTS -> {
        val labels = profile.involvements.entries.sortedBy { it.key.name }.map { (muscle, weight) ->
            "${stringResource(muscle.labelResource())}: $weight"
        }
        labels.joinToString(", ").ifEmpty { stringResource(Res.string.equipment_profile_none) }
    }
    ExerciseProfileGroup.LOAD -> stringResource(
        when (profile.loadCapability) {
            ExerciseLoadCapability.EXTERNAL -> Res.string.equipment_load_external
            ExerciseLoadCapability.BODYWEIGHT_ONLY -> Res.string.equipment_load_bodyweight
            ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> Res.string.equipment_load_bodyweight_added
            ExerciseLoadCapability.UNSPECIFIED -> Res.string.equipment_load_unspecified
        }
    )
    ExerciseProfileGroup.UNILATERAL -> stringResource(
        if (profile.isUnilateral) {
            Res.string.equipment_profile_yes
        } else {
            Res.string.equipment_profile_no
        }
    )
}
