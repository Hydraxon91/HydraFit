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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.DataCategoryUsage
import com.hydrafit.app.core.domain.storage.DataCount
import com.hydrafit.app.core.domain.storage.DataCountKind
import com.hydrafit.app.core.domain.storage.StorageUsage
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_encryption
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_encryption_key
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_export_failed
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_not_configured
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_unavailable
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_folder_unsupported
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_too_large
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_unknown
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_write_failed
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_failed_write_unverified
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_run_now
import hydrafit.feature.settings.generated.resources.settings_automatic_backup_status_run_succeeded
import hydrafit.feature.settings.generated.resources.settings_backup_cancel
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
import hydrafit.feature.settings.generated.resources.settings_model_action_failed
import hydrafit.feature.settings.generated.resources.settings_model_remove
import hydrafit.feature.settings.generated.resources.settings_storage_backup_disabled
import hydrafit.feature.settings.generated.resources.settings_storage_category_all_data
import hydrafit.feature.settings.generated.resources.settings_storage_category_backup_files
import hydrafit.feature.settings.generated.resources.settings_storage_category_body_and_records
import hydrafit.feature.settings.generated.resources.settings_storage_category_exercise_data
import hydrafit.feature.settings.generated.resources.settings_storage_category_on_device_model
import hydrafit.feature.settings.generated.resources.settings_storage_category_planning
import hydrafit.feature.settings.generated.resources.settings_storage_category_training_setup
import hydrafit.feature.settings.generated.resources.settings_storage_category_workout_history
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_all
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_all_kept
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_category_kept
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_model
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_model_kept
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_snapshots
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_snapshots_kept
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_snapshots_none
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_title
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_type
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_word
import hydrafit.feature.settings.generated.resources.settings_storage_confirm_word_label
import hydrafit.feature.settings.generated.resources.settings_storage_count_accepted_plans
import hydrafit.feature.settings.generated.resources.settings_storage_count_activations
import hydrafit.feature.settings.generated.resources.settings_storage_count_body_weight
import hydrafit.feature.settings.generated.resources.settings_storage_count_custom_exercises
import hydrafit.feature.settings.generated.resources.settings_storage_count_equipment_profiles
import hydrafit.feature.settings.generated.resources.settings_storage_count_line
import hydrafit.feature.settings.generated.resources.settings_storage_count_personal_records
import hydrafit.feature.settings.generated.resources.settings_storage_count_routines
import hydrafit.feature.settings.generated.resources.settings_storage_count_scheduled_workouts
import hydrafit.feature.settings.generated.resources.settings_storage_count_session_notes
import hydrafit.feature.settings.generated.resources.settings_storage_count_sessions
import hydrafit.feature.settings.generated.resources.settings_storage_count_sets
import hydrafit.feature.settings.generated.resources.settings_storage_database_size
import hydrafit.feature.settings.generated.resources.settings_storage_description
import hydrafit.feature.settings.generated.resources.settings_storage_model_installed
import hydrafit.feature.settings.generated.resources.settings_storage_model_not_installed
import hydrafit.feature.settings.generated.resources.settings_storage_no_data
import hydrafit.feature.settings.generated.resources.settings_storage_remove
import hydrafit.feature.settings.generated.resources.settings_storage_remove_all
import hydrafit.feature.settings.generated.resources.settings_storage_remove_failed
import hydrafit.feature.settings.generated.resources.settings_storage_removed_category
import hydrafit.feature.settings.generated.resources.settings_storage_removed_model
import hydrafit.feature.settings.generated.resources.settings_storage_removed_snapshots
import hydrafit.feature.settings.generated.resources.settings_storage_section
import hydrafit.feature.settings.generated.resources.settings_storage_size_bytes
import hydrafit.feature.settings.generated.resources.settings_storage_size_gb
import hydrafit.feature.settings.generated.resources.settings_storage_size_kb
import hydrafit.feature.settings.generated.resources.settings_storage_size_mb
import hydrafit.feature.settings.generated.resources.settings_storage_size_unavailable
import hydrafit.feature.settings.generated.resources.settings_storage_snapshots
import hydrafit.feature.settings.generated.resources.settings_storage_snapshots_count
import hydrafit.feature.settings.generated.resources.settings_storage_snapshots_none
import hydrafit.feature.settings.generated.resources.settings_storage_snapshots_not_configured
import hydrafit.feature.settings.generated.resources.settings_storage_usage_unavailable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Settings surface for inspecting and removing stored data: the database size, the recorded
 * automatic snapshots, the on-device model and one row per data category with its exact counts.
 * Every removal first opens a confirmation dialog, so a single tap never deletes anything.
 */
@Composable
fun StorageDataRoute(
    modifier: Modifier = Modifier,
    viewModel: StorageDataViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxWidth().storageTestTagsAsResourceId(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_storage_section),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.settings_storage_description),
            style = MaterialTheme.typography.bodySmall
        )
        val usage = state.usage
        if (usage == null) {
            Text(
                text = stringResource(Res.string.settings_storage_usage_unavailable),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = stringResource(
                    Res.string.settings_storage_database_size,
                    formatBytes(usage.databaseBytes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            SnapshotRow(
                usage = usage,
                enabled = !state.inProgress,
                onRemove = { viewModel.request(DataCategory.BACKUP_FILES) }
            )
        }
        ModelStateRow(state = state, onRemove = viewModel::removeModel)
        usage?.categories?.forEach { categoryUsage ->
            CategoryRow(
                usage = categoryUsage,
                enabled = !state.inProgress,
                onRemove = { viewModel.request(categoryUsage.category) }
            )
        }
        AllDataRow(enabled = !state.inProgress, onRemove = {
            viewModel.request(DataCategory.ALL_DATA)
        })
        state.status?.let { status ->
            Text(
                text = statusMessage(status),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    state.pending?.let { category ->
        StorageRemoveDialog(state = state, category = category, viewModel = viewModel)
    }
}

@Composable
private fun SnapshotRow(usage: StorageUsage, enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .testTag(StorageDataTestTags.categoryRow(DataCategory.BACKUP_FILES)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                Res.string.settings_storage_snapshots,
                snapshotsText(usage.automaticBackupFileCount, usage.automaticBackupBytes)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        val fileCount = usage.automaticBackupFileCount
        if (fileCount != null && fileCount > 0) {
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.testTag(
                    StorageDataTestTags.removeAction(DataCategory.BACKUP_FILES)
                )
            ) {
                Text(stringResource(Res.string.settings_storage_remove))
            }
        }
    }
}

@Composable
private fun ModelStateRow(state: StorageDataUiState, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(StorageDataTestTags.MODEL_ROW),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                if (state.modelInstalled) {
                    Res.string.settings_storage_model_installed
                } else {
                    Res.string.settings_storage_model_not_installed
                }
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (state.modelInstalled) {
            TextButton(
                onClick = onRemove,
                enabled = !state.inProgress,
                modifier = Modifier.testTag(
                    StorageDataTestTags.removeAction(DataCategory.ON_DEVICE_MODEL)
                )
            ) {
                Text(stringResource(Res.string.settings_model_remove))
            }
        }
    }
}

@Composable
private fun CategoryRow(usage: DataCategoryUsage, enabled: Boolean, onRemove: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .testTag(StorageDataTestTags.categoryRow(usage.category)),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(usage.category.labelResource()),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.testTag(StorageDataTestTags.removeAction(usage.category))
            ) {
                Text(stringResource(Res.string.settings_storage_remove))
            }
        }
        if (usage.counts.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_storage_no_data),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            CountLines(usage.counts)
        }
    }
}

@Composable
private fun AllDataRow(enabled: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .testTag(StorageDataTestTags.categoryRow(DataCategory.ALL_DATA)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.settings_storage_category_all_data),
            style = MaterialTheme.typography.bodyMedium
        )
        TextButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.testTag(StorageDataTestTags.removeAction(DataCategory.ALL_DATA))
        ) {
            Text(stringResource(Res.string.settings_storage_remove_all))
        }
    }
}

@Composable
private fun CountLines(counts: List<DataCount>) {
    counts.forEach { count ->
        Text(
            text = stringResource(
                Res.string.settings_storage_count_line,
                stringResource(count.kind.labelResource()),
                count.count
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun StorageRemoveDialog(
    state: StorageDataUiState,
    category: DataCategory,
    viewModel: StorageDataViewModel
) {
    val confirmed = category != DataCategory.ALL_DATA || isTypedConfirmationValid(state)
    AlertDialog(
        modifier = Modifier.storageTestTagsAsResourceId(),
        onDismissRequest = viewModel::cancel,
        title = {
            Text(
                stringResource(
                    Res.string.settings_storage_confirm_title,
                    stringResource(category.labelResource())
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.storageTestTagsAsResourceId(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmBody(state = state, category = category)
                if (category == DataCategory.ALL_DATA) {
                    Text(
                        text = stringResource(
                            Res.string.settings_storage_confirm_type,
                            stringResource(Res.string.settings_storage_confirm_word)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = state.typedConfirmation,
                        onValueChange = viewModel::typedConfirmation,
                        label = {
                            Text(stringResource(Res.string.settings_storage_confirm_word_label))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .testTag(StorageDataTestTags.TYPED_CONFIRMATION)
                    )
                }
                state.backupStatus?.let { backup ->
                    Text(
                        text = backupStatusMessage(backup),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::backUpNow, enabled = !state.inProgress) {
                    Text(stringResource(Res.string.settings_automatic_backup_run_now))
                }
                TextButton(
                    onClick = viewModel::confirm,
                    enabled = confirmed && !state.inProgress,
                    modifier = Modifier.testTag(StorageDataTestTags.CONFIRM_ACTION)
                ) {
                    Text(
                        text = stringResource(
                            if (category == DataCategory.ALL_DATA) {
                                Res.string.settings_storage_remove_all
                            } else {
                                Res.string.settings_storage_remove
                            }
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::cancel) {
                Text(stringResource(Res.string.settings_backup_cancel))
            }
        }
    )
}

@Composable
private fun ConfirmBody(state: StorageDataUiState, category: DataCategory) {
    when (category) {
        DataCategory.BACKUP_FILES -> {
            val count = state.usage?.automaticBackupFileCount
            Text(
                text = if (count != null) {
                    stringResource(Res.string.settings_storage_confirm_snapshots, count)
                } else {
                    stringResource(Res.string.settings_storage_confirm_snapshots_none)
                }
            )
            Disclosure(Res.string.settings_storage_confirm_snapshots_kept)
        }

        DataCategory.ON_DEVICE_MODEL -> {
            Text(stringResource(Res.string.settings_storage_confirm_model))
            Disclosure(Res.string.settings_storage_confirm_model_kept)
        }

        DataCategory.ALL_DATA -> {
            Text(stringResource(Res.string.settings_storage_confirm_all))
            state.usage?.categories?.forEach { categoryUsage ->
                CountLines(categoryUsage.counts)
            }
            Disclosure(Res.string.settings_storage_confirm_all_kept)
        }

        else -> {
            val counts = state.usage?.categories
                ?.firstOrNull { it.category == category }
                ?.counts
                .orEmpty()
            if (counts.isEmpty()) {
                Text(stringResource(Res.string.settings_storage_no_data))
            } else {
                CountLines(counts)
            }
            Disclosure(Res.string.settings_storage_confirm_category_kept)
        }
    }
}

@Composable
private fun Disclosure(resource: StringResource) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.bodySmall
    )
}

private fun isTypedConfirmationValid(state: StorageDataUiState): Boolean =
    state.typedConfirmation.trim()
        .equals(StorageDataViewModel.ALL_DATA_CONFIRMATION, ignoreCase = true)

@Composable
private fun snapshotsText(fileCount: Int?, bytes: Long?): String = when {
    fileCount == null -> stringResource(Res.string.settings_storage_snapshots_not_configured)
    fileCount == 0 -> stringResource(Res.string.settings_storage_snapshots_none)
    else -> {
        val size = if (bytes == null) {
            stringResource(Res.string.settings_storage_size_unavailable)
        } else {
            formatBytes(bytes)
        }
        stringResource(Res.string.settings_storage_snapshots_count, fileCount, size)
    }
}

private const val BYTES_PER_UNIT = 1024L

@Composable
private fun formatBytes(bytes: Long): String = when {
    bytes < BYTES_PER_UNIT -> stringResource(Res.string.settings_storage_size_bytes, bytes)
    bytes < BYTES_PER_UNIT * BYTES_PER_UNIT ->
        stringResource(Res.string.settings_storage_size_kb, roundDiv(bytes, BYTES_PER_UNIT))

    bytes < BYTES_PER_UNIT * BYTES_PER_UNIT * BYTES_PER_UNIT ->
        stringResource(
            Res.string.settings_storage_size_mb,
            roundDiv(bytes, BYTES_PER_UNIT * BYTES_PER_UNIT)
        )

    else -> stringResource(
        Res.string.settings_storage_size_gb,
        roundDiv(bytes, BYTES_PER_UNIT * BYTES_PER_UNIT * BYTES_PER_UNIT)
    )
}

private fun roundDiv(value: Long, divisor: Long): Long = (value + divisor / 2) / divisor

@Composable
private fun statusMessage(status: StorageDataStatus): String = when (status) {
    is StorageDataStatus.CategoryRemoved -> stringResource(
        Res.string.settings_storage_removed_category,
        stringResource(status.category.labelResource())
    )

    is StorageDataStatus.BackupFilesRemoved ->
        stringResource(Res.string.settings_storage_removed_snapshots, status.count)

    StorageDataStatus.ModelRemoved -> stringResource(Res.string.settings_storage_removed_model)
    StorageDataStatus.ModelRemovalFailed ->
        stringResource(Res.string.settings_model_action_failed)

    is StorageDataStatus.Failed -> status.failure?.messageResource()?.let {
        stringResource(it)
    } ?: stringResource(Res.string.settings_storage_remove_failed)
}

@Composable
private fun backupStatusMessage(status: StorageBackupStatus): String = when (status) {
    StorageBackupStatus.Succeeded ->
        stringResource(Res.string.settings_automatic_backup_status_run_succeeded)

    StorageBackupStatus.Disabled -> stringResource(Res.string.settings_storage_backup_disabled)
    is StorageBackupStatus.Failed -> stringResource(status.kind.messageResource())
}

private fun AutomaticBackupFailureKind.messageResource(): StringResource = when (this) {
    AutomaticBackupFailureKind.FOLDER_NOT_CONFIGURED ->
        Res.string.settings_automatic_backup_failed_folder_not_configured

    AutomaticBackupFailureKind.FOLDER_UNAVAILABLE ->
        Res.string.settings_automatic_backup_failed_folder_unavailable

    AutomaticBackupFailureKind.FOLDER_UNSUPPORTED ->
        Res.string.settings_automatic_backup_failed_folder_unsupported

    AutomaticBackupFailureKind.EXPORT_FAILED ->
        Res.string.settings_automatic_backup_failed_export_failed

    AutomaticBackupFailureKind.TOO_LARGE -> Res.string.settings_automatic_backup_failed_too_large
    AutomaticBackupFailureKind.ENCRYPTION_FAILED ->
        Res.string.settings_automatic_backup_failed_encryption

    AutomaticBackupFailureKind.ENCRYPTION_KEY_UNAVAILABLE ->
        Res.string.settings_automatic_backup_failed_encryption_key

    AutomaticBackupFailureKind.WRITE_FAILED ->
        Res.string.settings_automatic_backup_failed_write_failed

    AutomaticBackupFailureKind.WRITE_UNVERIFIED ->
        Res.string.settings_automatic_backup_failed_write_unverified

    AutomaticBackupFailureKind.UNKNOWN -> Res.string.settings_automatic_backup_failed_unknown
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

private fun DataCategory.labelResource(): StringResource = when (this) {
    DataCategory.WORKOUT_HISTORY -> Res.string.settings_storage_category_workout_history
    DataCategory.PLANNING -> Res.string.settings_storage_category_planning
    DataCategory.BODY_AND_RECORDS -> Res.string.settings_storage_category_body_and_records
    DataCategory.TRAINING_SETUP -> Res.string.settings_storage_category_training_setup
    DataCategory.EXERCISE_DATA -> Res.string.settings_storage_category_exercise_data
    DataCategory.BACKUP_FILES -> Res.string.settings_storage_category_backup_files
    DataCategory.ON_DEVICE_MODEL -> Res.string.settings_storage_category_on_device_model
    DataCategory.ALL_DATA -> Res.string.settings_storage_category_all_data
}

private fun DataCountKind.labelResource(): StringResource = when (this) {
    DataCountKind.SESSIONS -> Res.string.settings_storage_count_sessions
    DataCountKind.SETS -> Res.string.settings_storage_count_sets
    DataCountKind.SESSION_NOTES -> Res.string.settings_storage_count_session_notes
    DataCountKind.ACCEPTED_PLANS -> Res.string.settings_storage_count_accepted_plans
    DataCountKind.ROUTINES -> Res.string.settings_storage_count_routines
    DataCountKind.ACTIVATIONS -> Res.string.settings_storage_count_activations
    DataCountKind.SCHEDULED_WORKOUTS -> Res.string.settings_storage_count_scheduled_workouts
    DataCountKind.BODY_WEIGHT_MEASUREMENTS -> Res.string.settings_storage_count_body_weight
    DataCountKind.PERSONAL_RECORDS -> Res.string.settings_storage_count_personal_records
    DataCountKind.EQUIPMENT_PROFILES -> Res.string.settings_storage_count_equipment_profiles
    DataCountKind.CUSTOM_EXERCISES -> Res.string.settings_storage_count_custom_exercises
}
