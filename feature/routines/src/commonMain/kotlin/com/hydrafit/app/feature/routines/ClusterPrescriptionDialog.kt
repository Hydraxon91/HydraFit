package com.hydrafit.app.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.routine.ClusterKind
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.ClusterSegmentTarget
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.unit.formatWeight
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.routines_cancel
import hydrafit.feature.routines.generated.resources.routines_cluster_add_segment
import hydrafit.feature.routines.generated.resources.routines_cluster_error_drop_load
import hydrafit.feature.routines.generated.resources.routines_cluster_error_load
import hydrafit.feature.routines.generated.resources.routines_cluster_error_reps
import hydrafit.feature.routines.generated.resources.routines_cluster_error_rest
import hydrafit.feature.routines.generated.resources.routines_cluster_error_rest_pause_load
import hydrafit.feature.routines.generated.resources.routines_cluster_error_rest_pause_rest
import hydrafit.feature.routines.generated.resources.routines_cluster_error_rir
import hydrafit.feature.routines.generated.resources.routines_cluster_error_too_few_segments
import hydrafit.feature.routines.generated.resources.routines_cluster_error_too_many_segments
import hydrafit.feature.routines.generated.resources.routines_cluster_kind_drop_set
import hydrafit.feature.routines.generated.resources.routines_cluster_kind_label
import hydrafit.feature.routines.generated.resources.routines_cluster_kind_rest_pause
import hydrafit.feature.routines.generated.resources.routines_cluster_remove_segment
import hydrafit.feature.routines.generated.resources.routines_cluster_rest_label
import hydrafit.feature.routines.generated.resources.routines_cluster_segment_label
import hydrafit.feature.routines.generated.resources.routines_cluster_segment_load_label
import hydrafit.feature.routines.generated.resources.routines_cluster_segment_reps_label
import hydrafit.feature.routines.generated.resources.routines_cluster_segment_rir_label
import hydrafit.feature.routines.generated.resources.routines_cluster_summary_kind_drop_set
import hydrafit.feature.routines.generated.resources.routines_cluster_summary_kind_rest_pause
import hydrafit.feature.routines.generated.resources.routines_cluster_title
import hydrafit.feature.routines.generated.resources.routines_progression_clear
import hydrafit.feature.routines.generated.resources.routines_set_targets_apply
import org.jetbrains.compose.resources.stringResource

/** The parsed dialog input, or the specific field the user must fix. */
internal sealed interface ClusterParseResult {
    data class Valid(val prescription: ClusterPrescription) : ClusterParseResult

    data class Invalid(val error: ClusterError) : ClusterParseResult
}

/**
 * Builds the prescription the current dialog inputs describe. Unparsable numbers map to the field
 * they belong to; a parsed but invalid prescription is mapped back to the rule it breaks. Nothing
 * is written when this returns [ClusterParseResult.Invalid].
 */
internal fun parseClusterEditor(editor: ClusterEditorState): ClusterParseResult {
    if (editor.segments.size < ClusterPrescription.MIN_SEGMENTS) {
        return ClusterParseResult.Invalid(ClusterError.TOO_FEW_SEGMENTS)
    }
    if (editor.segments.size > ClusterPrescription.MAX_SEGMENTS) {
        return ClusterParseResult.Invalid(ClusterError.TOO_MANY_SEGMENTS)
    }
    val segments = editor.segments.map { segment ->
        val reps = segment.reps.trim().toIntOrNull()
            ?: return ClusterParseResult.Invalid(ClusterError.REPS_RANGE)
        val load = segment.loadKg.trim().toDoubleOrNull()
            ?: return ClusterParseResult.Invalid(ClusterError.LOAD)
        val rir = segment.rir.trim().let { text ->
            if (text.isEmpty()) {
                null
            } else {
                text.toIntOrNull()
                    ?: return ClusterParseResult.Invalid(ClusterError.RIR_RANGE)
            }
        }
        ClusterSegmentTarget(reps = reps, weightKg = load, rir = rir)
    }
    val rest = editor.restSeconds.trim().let { text ->
        if (text.isEmpty()) {
            null
        } else {
            text.toIntOrNull()
                ?: return ClusterParseResult.Invalid(ClusterError.REST_RANGE)
        }
    }
    val prescription = ClusterPrescription(
        kind = editor.kind,
        segments = segments,
        restSeconds = rest
    )
    return try {
        prescription.requireValid()
        ClusterParseResult.Valid(prescription)
    } catch (_: IllegalArgumentException) {
        ClusterParseResult.Invalid(prescription.fieldError())
    }
}

/**
 * Maps a rejected prescription back to its offending rule by checking the domain rule's
 * conditions with the domain's own constants, so no numeric bound is duplicated here.
 */
private fun ClusterPrescription.fieldError(): ClusterError {
    val loads = segments.map { it.weightKg }
    val rest = restSeconds
    return when {
        segments.any { it.reps !in ClusterSegmentTarget.MIN_REPS..ClusterSegmentTarget.MAX_REPS } ->
            ClusterError.REPS_RANGE
        segments.any { !it.weightKg.isFinite() || it.weightKg <= 0.0 } -> ClusterError.LOAD
        segments.any {
            it.rir != null && it.rir !in SetTarget.MIN_RIR..SetTarget.MAX_RIR
        } -> ClusterError.RIR_RANGE
        rest != null && rest !in 0..Superset.MAX_REST_SECONDS ->
            ClusterError.REST_RANGE
        kind == ClusterKind.DROP_SET && loads.zipWithNext().any { (a, b) -> b >= a } ->
            ClusterError.DROP_LOAD_NOT_DECREASING
        kind == ClusterKind.REST_PAUSE && loads.distinct().size != 1 ->
            ClusterError.REST_PAUSE_LOAD_NOT_REPEATED
        kind == ClusterKind.REST_PAUSE && (rest == null || rest <= 0) ->
            ClusterError.REST_PAUSE_NO_REST
        else -> ClusterError.REST_RANGE
    }
}

/** The load sequence shown in the slot summary, e.g. "100/80/60", in stored kilograms. */
internal fun clusterSummaryLoads(prescription: ClusterPrescription): String =
    prescription.segments.joinToString("/") { formatWeight(it.weightKg) }

/** The segment count shown in the summary; a cluster's parts are segments, never sets. */
internal fun clusterSummarySegmentCount(prescription: ClusterPrescription): Int =
    prescription.segments.size

@Composable
internal fun ClusterPrescriptionDialog(
    state: ClusterEditorState,
    hasExistingCluster: Boolean,
    onKindChanged: (ClusterKind) -> Unit,
    onSegmentRepsChanged: (Int, String) -> Unit,
    onSegmentLoadChanged: (Int, String) -> Unit,
    onSegmentRirChanged: (Int, String) -> Unit,
    onRestChanged: (String) -> Unit,
    onAddSegment: () -> Unit,
    onRemoveSegment: () -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.routines_cluster_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.routines_cluster_kind_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ClusterKind.entries.forEach { kind ->
                        FilterChip(
                            selected = state.kind == kind,
                            onClick = { onKindChanged(kind) },
                            label = { Text(clusterKindLabel(kind)) }
                        )
                    }
                }
                state.segments.forEachIndexed { index, segment ->
                    Text(
                        text = stringResource(Res.string.routines_cluster_segment_label, index + 1),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = segment.reps,
                            onValueChange = { onSegmentRepsChanged(index, it) },
                            label = {
                                Text(stringResource(Res.string.routines_cluster_segment_reps_label))
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = segment.loadKg,
                            onValueChange = { onSegmentLoadChanged(index, it) },
                            label = {
                                Text(stringResource(Res.string.routines_cluster_segment_load_label))
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = segment.rir,
                            onValueChange = { onSegmentRirChanged(index, it) },
                            label = {
                                Text(stringResource(Res.string.routines_cluster_segment_rir_label))
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = state.restSeconds,
                    onValueChange = onRestChanged,
                    label = { Text(stringResource(Res.string.routines_cluster_rest_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { error ->
                    Text(
                        text = clusterErrorText(error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onAddSegment,
                        enabled = state.segments.size < ClusterPrescription.MAX_SEGMENTS
                    ) {
                        Text(stringResource(Res.string.routines_cluster_add_segment))
                    }
                    TextButton(
                        onClick = onRemoveSegment,
                        enabled = state.segments.size > ClusterPrescription.MIN_SEGMENTS
                    ) {
                        Text(stringResource(Res.string.routines_cluster_remove_segment))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(Res.string.routines_set_targets_apply))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasExistingCluster) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(Res.string.routines_progression_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.routines_cancel))
                }
            }
        }
    )
}

@Composable
private fun clusterKindLabel(kind: ClusterKind): String = when (kind) {
    ClusterKind.DROP_SET -> stringResource(Res.string.routines_cluster_kind_drop_set)
    ClusterKind.REST_PAUSE -> stringResource(Res.string.routines_cluster_kind_rest_pause)
}

/** The lower-case kind label the slot summary uses, e.g. "drop set". */
@Composable
internal fun clusterSummaryKindLabel(kind: ClusterKind): String = when (kind) {
    ClusterKind.DROP_SET -> stringResource(Res.string.routines_cluster_summary_kind_drop_set)
    ClusterKind.REST_PAUSE -> stringResource(Res.string.routines_cluster_summary_kind_rest_pause)
}

@Composable
private fun clusterErrorText(error: ClusterError): String = when (error) {
    ClusterError.TOO_FEW_SEGMENTS -> stringResource(
        Res.string.routines_cluster_error_too_few_segments,
        ClusterPrescription.MIN_SEGMENTS
    )
    ClusterError.TOO_MANY_SEGMENTS -> stringResource(
        Res.string.routines_cluster_error_too_many_segments,
        ClusterPrescription.MAX_SEGMENTS
    )
    ClusterError.DROP_LOAD_NOT_DECREASING ->
        stringResource(Res.string.routines_cluster_error_drop_load)
    ClusterError.REST_PAUSE_LOAD_NOT_REPEATED ->
        stringResource(Res.string.routines_cluster_error_rest_pause_load)
    ClusterError.REST_PAUSE_NO_REST ->
        stringResource(Res.string.routines_cluster_error_rest_pause_rest)
    ClusterError.REPS_RANGE -> stringResource(
        Res.string.routines_cluster_error_reps,
        ClusterSegmentTarget.MIN_REPS,
        ClusterSegmentTarget.MAX_REPS
    )
    ClusterError.LOAD -> stringResource(Res.string.routines_cluster_error_load)
    ClusterError.RIR_RANGE -> stringResource(
        Res.string.routines_cluster_error_rir,
        SetTarget.MIN_RIR,
        SetTarget.MAX_RIR
    )
    ClusterError.REST_RANGE -> stringResource(
        Res.string.routines_cluster_error_rest,
        Superset.MAX_REST_SECONDS
    )
}
