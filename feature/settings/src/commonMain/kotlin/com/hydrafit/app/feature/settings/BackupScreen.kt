package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupMergeExcludedCategory
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_backup_cancel
import hydrafit.feature.settings.generated.resources.settings_backup_confirm
import hydrafit.feature.settings.generated.resources.settings_backup_description
import hydrafit.feature.settings.generated.resources.settings_backup_error_catalog_mismatch
import hydrafit.feature.settings.generated.resources.settings_backup_error_duplicate_id
import hydrafit.feature.settings.generated.resources.settings_backup_error_encryption_failed
import hydrafit.feature.settings.generated.resources.settings_backup_error_encryption_key_unavailable
import hydrafit.feature.settings.generated.resources.settings_backup_error_invalid_reference
import hydrafit.feature.settings.generated.resources.settings_backup_error_invalid_value
import hydrafit.feature.settings.generated.resources.settings_backup_error_io
import hydrafit.feature.settings.generated.resources.settings_backup_error_malformed
import hydrafit.feature.settings.generated.resources.settings_backup_error_over_limit
import hydrafit.feature.settings.generated.resources.settings_backup_error_startup_unstable
import hydrafit.feature.settings.generated.resources.settings_backup_error_too_large
import hydrafit.feature.settings.generated.resources.settings_backup_error_unknown_catalog
import hydrafit.feature.settings.generated.resources.settings_backup_error_unsupported_version
import hydrafit.feature.settings.generated.resources.settings_backup_exported
import hydrafit.feature.settings.generated.resources.settings_backup_history_csv_description
import hydrafit.feature.settings.generated.resources.settings_backup_history_csv_exported
import hydrafit.feature.settings.generated.resources.settings_backup_merge_action
import hydrafit.feature.settings.generated.resources.settings_backup_merge_body
import hydrafit.feature.settings.generated.resources.settings_backup_merge_dropped_links
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_accepted_plans
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_activations
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_equipment_profiles
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_import_ledger
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_line
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_occurrences
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_routines
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_settings
import hydrafit.feature.settings.generated.resources.settings_backup_merge_excluded_title
import hydrafit.feature.settings.generated.resources.settings_backup_merge_kept_local
import hydrafit.feature.settings.generated.resources.settings_backup_merge_nothing_new
import hydrafit.feature.settings.generated.resources.settings_backup_merge_other_setup
import hydrafit.feature.settings.generated.resources.settings_backup_merge_skipped_open
import hydrafit.feature.settings.generated.resources.settings_backup_merge_skipped_unattached
import hydrafit.feature.settings.generated.resources.settings_backup_merge_staged
import hydrafit.feature.settings.generated.resources.settings_backup_merge_title
import hydrafit.feature.settings.generated.resources.settings_backup_password
import hydrafit.feature.settings.generated.resources.settings_backup_preview_body
import hydrafit.feature.settings.generated.resources.settings_backup_preview_title
import hydrafit.feature.settings.generated.resources.settings_backup_restored
import hydrafit.feature.settings.generated.resources.settings_backup_section
import hydrafit.feature.settings.generated.resources.settings_backup_unlock
import hydrafit.feature.settings.generated.resources.settings_backup_unlock_title
import hydrafit.feature.settings.generated.resources.settings_backup_unsupported
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupRoute(modifier: Modifier = Modifier, viewModel: BackupViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_backup_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_backup_description),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isSupported) {
            BackupSection(
                isSupported = state.isSupported,
                busy = state.inProgress,
                onExportTo = viewModel::export,
                onExportCsvTo = viewModel::exportCsv,
                onImportFrom = viewModel::`import`
            )
            Text(
                text = stringResource(Res.string.settings_backup_history_csv_description),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = stringResource(Res.string.settings_backup_unsupported),
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.status?.let { status ->
            val message = when (status) {
                BackupStatus.Exported -> stringResource(Res.string.settings_backup_exported)
                BackupStatus.HistoryCsvExported ->
                    stringResource(Res.string.settings_backup_history_csv_exported)

                BackupStatus.RestoreStaged -> stringResource(Res.string.settings_backup_restored)
                BackupStatus.MergeStaged -> stringResource(Res.string.settings_backup_merge_staged)
                is BackupStatus.Failed -> stringResource(status.failure.messageResource())
            }
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPreview,
            title = { Text(stringResource(Res.string.settings_backup_preview_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            Res.string.settings_backup_preview_body,
                            preview.totalRecords,
                            preview.workoutSets,
                            preview.customExercises
                        )
                    )
                    state.mergePreview?.let { merge -> MergePreviewSummary(merge) }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::confirmRestore, enabled = !state.inProgress) {
                        Text(stringResource(Res.string.settings_backup_confirm))
                    }
                    state.mergePreview?.let { merge ->
                        TextButton(
                            onClick = viewModel::confirmMerge,
                            enabled = !state.inProgress && !merge.isEmpty,
                            modifier = Modifier.testTag(BackupTestTags.MERGE_ACTION)
                        ) {
                            Text(stringResource(Res.string.settings_backup_merge_action))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPreview) {
                    Text(stringResource(Res.string.settings_backup_cancel))
                }
            }
        )
    }

    if (state.unlocking) {
        var unlockPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::cancelUnlock,
            title = { Text(stringResource(Res.string.settings_backup_unlock_title)) },
            text = {
                OutlinedTextField(
                    value = unlockPassword,
                    onValueChange = { unlockPassword = it },
                    label = { Text(stringResource(Res.string.settings_backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.unlockImport(unlockPassword) },
                    enabled = unlockPassword.isNotEmpty() && !state.inProgress
                ) {
                    Text(stringResource(Res.string.settings_backup_unlock))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelUnlock) {
                    Text(stringResource(Res.string.settings_backup_cancel))
                }
            }
        )
    }
}

/**
 * The add-only disclosure shown with the replace copy: what a merge would insert, what it keeps
 * local, what it skips, and which collections it does not carry. It never mutates anything.
 */
@Composable
private fun MergePreviewSummary(merge: BackupMergePreviewUi) {
    Column(
        modifier = Modifier.testTag(BackupTestTags.MERGE_SUMMARY),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_backup_merge_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                Res.string.settings_backup_merge_body,
                merge.sessions,
                merge.sets,
                merge.insertCount
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (merge.isEmpty) {
            Text(
                text = stringResource(Res.string.settings_backup_merge_nothing_new),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (merge.keptLocalSessions > 0) {
            Text(
                text = stringResource(
                    Res.string.settings_backup_merge_kept_local,
                    merge.keptLocalSessions
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (merge.skippedOpenSessions > 0) {
            Text(
                text = stringResource(
                    Res.string.settings_backup_merge_skipped_open,
                    merge.skippedOpenSessions
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (merge.skippedUnattachedSets > 0) {
            Text(
                text = stringResource(
                    Res.string.settings_backup_merge_skipped_unattached,
                    merge.skippedUnattachedSets
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (merge.droppedOccurrenceLinks > 0) {
            Text(
                text = stringResource(
                    Res.string.settings_backup_merge_dropped_links,
                    merge.droppedOccurrenceLinks
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (merge.excluded.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.settings_backup_merge_excluded_title),
                style = MaterialTheme.typography.bodySmall
            )
            merge.excluded.forEach { excluded ->
                Text(
                    text = stringResource(
                        Res.string.settings_backup_merge_excluded_line,
                        stringResource(excluded.category.labelResource()),
                        excluded.count
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text(
            text = stringResource(Res.string.settings_backup_merge_other_setup),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun BackupMergeExcludedCategory.labelResource(): StringResource = when (this) {
    BackupMergeExcludedCategory.ACCEPTED_PLANS ->
        Res.string.settings_backup_merge_excluded_accepted_plans

    BackupMergeExcludedCategory.ROUTINES -> Res.string.settings_backup_merge_excluded_routines
    BackupMergeExcludedCategory.ACTIVATIONS ->
        Res.string.settings_backup_merge_excluded_activations

    BackupMergeExcludedCategory.OCCURRENCES ->
        Res.string.settings_backup_merge_excluded_occurrences

    BackupMergeExcludedCategory.EQUIPMENT_PROFILES ->
        Res.string.settings_backup_merge_excluded_equipment_profiles

    BackupMergeExcludedCategory.SETTINGS -> Res.string.settings_backup_merge_excluded_settings
    BackupMergeExcludedCategory.IMPORT_LEDGER ->
        Res.string.settings_backup_merge_excluded_import_ledger
}

private fun BackupFailure.messageResource(): StringResource = when (this) {
    BackupFailure.MALFORMED -> Res.string.settings_backup_error_malformed
    BackupFailure.UNSUPPORTED_VERSION -> Res.string.settings_backup_error_unsupported_version
    BackupFailure.TOO_LARGE -> Res.string.settings_backup_error_too_large
    BackupFailure.OVER_LIMIT -> Res.string.settings_backup_error_over_limit
    BackupFailure.STARTUP_UNSTABLE -> Res.string.settings_backup_error_startup_unstable
    BackupFailure.CATALOG_MISMATCH -> Res.string.settings_backup_error_catalog_mismatch
    BackupFailure.UNKNOWN_CATALOG_ID -> Res.string.settings_backup_error_unknown_catalog
    BackupFailure.DUPLICATE_ID -> Res.string.settings_backup_error_duplicate_id
    BackupFailure.INVALID_REFERENCE -> Res.string.settings_backup_error_invalid_reference
    BackupFailure.INVALID_VALUE -> Res.string.settings_backup_error_invalid_value
    BackupFailure.ENCRYPTION_FAILED -> Res.string.settings_backup_error_encryption_failed
    BackupFailure.ENCRYPTION_KEY_UNAVAILABLE ->
        Res.string.settings_backup_error_encryption_key_unavailable

    BackupFailure.IO -> Res.string.settings_backup_error_io
}
