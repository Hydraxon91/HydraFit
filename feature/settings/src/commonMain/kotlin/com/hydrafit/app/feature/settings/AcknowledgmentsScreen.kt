package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.acknowledgments_author
import hydrafit.feature.settings.generated.resources.acknowledgments_back
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_exrx
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_free_exercise_db
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_garcia_valverde
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_krause_neto
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_martin_fuentes_ijerph
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_martin_fuentes_plos
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_oliva_lozano
import hydrafit.feature.settings.generated.resources.acknowledgments_credits_heading
import hydrafit.feature.settings.generated.resources.acknowledgments_license_heading
import hydrafit.feature.settings.generated.resources.acknowledgments_license_text
import hydrafit.feature.settings.generated.resources.acknowledgments_repo
import hydrafit.feature.settings.generated.resources.acknowledgments_title
import hydrafit.feature.settings.generated.resources.acknowledgments_version
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

val acknowledgmentsRoute: String = "settings/acknowledgments"

private const val REPOSITORY_URL = "https://github.com/Hydraxon91/HydraFit"

@Composable
fun AcknowledgmentsRoute(
    onBack: () -> Unit,
    viewModel: AcknowledgmentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AcknowledgmentsScreen(state = state, onBack = onBack)
}

@Composable
fun AcknowledgmentsScreen(
    state: AcknowledgmentsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val credits: List<StringResource> = listOf(
        Res.string.acknowledgments_credit_krause_neto,
        Res.string.acknowledgments_credit_martin_fuentes_plos,
        Res.string.acknowledgments_credit_martin_fuentes_ijerph,
        Res.string.acknowledgments_credit_oliva_lozano,
        Res.string.acknowledgments_credit_garcia_valverde,
        Res.string.acknowledgments_credit_free_exercise_db,
        Res.string.acknowledgments_credit_exrx
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.acknowledgments_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(Res.string.acknowledgments_version, state.versionName),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(Res.string.acknowledgments_credits_heading),
            style = MaterialTheme.typography.titleMedium
        )
        credits.forEach { credit ->
            Text(
                text = stringResource(credit),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = stringResource(Res.string.acknowledgments_license_heading),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(Res.string.acknowledgments_license_text),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(Res.string.acknowledgments_author),
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = { uriHandler.openUri(REPOSITORY_URL) }) {
            Text(stringResource(Res.string.acknowledgments_repo))
        }
        OutlinedButton(onClick = onBack) {
            Text(stringResource(Res.string.acknowledgments_back))
        }
    }
}
