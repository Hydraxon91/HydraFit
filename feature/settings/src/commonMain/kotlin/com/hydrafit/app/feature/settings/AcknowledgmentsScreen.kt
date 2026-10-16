package com.hydrafit.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hydrafit.feature.settings.generated.resources.Res
import hydrafit.feature.settings.generated.resources.acknowledgments_author
import hydrafit.feature.settings.generated.resources.acknowledgments_back
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_body_highlighter
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_exrx
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_free_exercise_db
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_garcia_valverde
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_krause_neto
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_martin_fuentes_ijerph
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_martin_fuentes_plos
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_musclemap
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_oliva_lozano
import hydrafit.feature.settings.generated.resources.acknowledgments_credit_unicode
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
private const val MUSCLEMAP_URL = "https://github.com/melihcolpan/MuscleMap"
private const val BODY_HIGHLIGHTER_URL =
    "https://github.com/HichamELBSI/react-native-body-highlighter"

/** One credit line; [sourceUrl] appends a tappable link to the upstream source. */
private data class Credit(val label: StringResource, val sourceUrl: String? = null)

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
    val credits: List<Credit> = listOf(
        Credit(Res.string.acknowledgments_credit_krause_neto),
        Credit(Res.string.acknowledgments_credit_martin_fuentes_plos),
        Credit(Res.string.acknowledgments_credit_martin_fuentes_ijerph),
        Credit(Res.string.acknowledgments_credit_oliva_lozano),
        Credit(Res.string.acknowledgments_credit_garcia_valverde),
        Credit(Res.string.acknowledgments_credit_free_exercise_db),
        Credit(Res.string.acknowledgments_credit_exrx),
        Credit(Res.string.acknowledgments_credit_unicode),
        Credit(Res.string.acknowledgments_credit_musclemap, MUSCLEMAP_URL),
        Credit(Res.string.acknowledgments_credit_body_highlighter, BODY_HIGHLIGHTER_URL)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
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
            CreditLine(credit)
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

@Composable
private fun CreditLine(credit: Credit) {
    val label = stringResource(credit.label)
    val url = credit.sourceUrl
    Text(
        text = if (url == null) {
            AnnotatedString(label)
        } else {
            buildAnnotatedString {
                append(label)
                append(" · ")
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) {
                    append(url.removePrefix("https://"))
                }
            }
        },
        style = MaterialTheme.typography.bodySmall
    )
}
