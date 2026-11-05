package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.settings_licenses_failed
import hydrafit.feature.settings.generated.resources.settings_licenses_loading
import hydrafit.feature.settings.generated.resources.settings_licenses_retry
import hydrafit.feature.settings.generated.resources.settings_licenses_title
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

internal const val LICENSES_ASSET_PATH: String = "files/licenses/THIRD-PARTY-NOTICES.txt"

/** The largest lazily composed text block; the complete asset is kept and concatenated intact. */
internal const val LICENSE_BLOCK_CHARS: Int = 3_500

sealed interface LicensesUiState {
    data object Loading : LicensesUiState
    data object Failed : LicensesUiState
    data class Ready(val blocks: List<String>) : LicensesUiState
}

/**
 * Splits the stored notice text into bounded blocks without dropping or duplicating a character.
 * Preferred boundaries are newlines; a single very long line is hard-split so every block stays
 * bounded. Joining the result always reproduces the exact source text.
 */
internal fun licenseTextBlocks(text: String, maxChars: Int = LICENSE_BLOCK_CHARS): List<String> {
    require(maxChars > 0)
    if (text.isEmpty()) return emptyList()
    val blocks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val limit = minOf(start + maxChars, text.length)
        var end = limit
        if (limit < text.length) {
            val newline = text.lastIndexOf('\n', limit - 1)
            if (newline >= start + maxChars / 2) end = newline + 1
        }
        blocks += text.substring(start, end)
        start = end
    }
    return blocks
}

/** A blank, truncated or unrelated asset is a read failure, never an empty success screen. */
internal fun isValidLicensesText(text: String): Boolean = text.length > 10_000 &&
    text.contains("HydraFit third-party notices") &&
    text.contains("Apache License") &&
    text.contains("Public Suffix List exact source")

/**
 * Reads the bundled notices. Cancellation propagates so leaving the page stops the read; other
 * failures become a retryable result. No network fallback exists.
 */
internal suspend fun loadLicenses(readAsset: suspend (String) -> ByteArray): Result<String> = try {
    val text = readAsset(LICENSES_ASSET_PATH).decodeToString()
    if (isValidLicensesText(text)) {
        Result.success(text)
    } else {
        Result.failure(IllegalStateException("bundled license text is empty or incomplete"))
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}

@Composable
fun OpenSourceLicensesRoute(
    onBack: () -> Unit,
    readAsset: suspend (String) -> ByteArray = { Res.readBytes(it) }
) {
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<LicensesUiState>(LicensesUiState.Loading) }
    LaunchedEffect(attempt) {
        state = LicensesUiState.Loading
        state = loadLicenses(readAsset).fold(
            onSuccess = { text -> LicensesUiState.Ready(licenseTextBlocks(text)) },
            onFailure = { LicensesUiState.Failed }
        )
    }
    OpenSourceLicensesScreen(
        state = state,
        onBack = onBack,
        onRetry = { attempt++ }
    )
}

@Composable
fun OpenSourceLicensesScreen(
    state: LicensesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "licenses_header") {
            SettingsPageHeader(
                title = stringResource(Res.string.settings_licenses_title),
                onBack = onBack
            )
        }
        when (state) {
            LicensesUiState.Loading -> item(key = "licenses_loading") {
                Text(
                    text = stringResource(Res.string.settings_licenses_loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            LicensesUiState.Failed -> item(key = "licenses_failed") {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.settings_licenses_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(Res.string.settings_licenses_retry))
                    }
                }
            }

            is LicensesUiState.Ready -> itemsIndexed(
                items = state.blocks,
                key = { index, _ -> "licenses_block_$index" }
            ) { _, block ->
                Text(
                    text = block,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
