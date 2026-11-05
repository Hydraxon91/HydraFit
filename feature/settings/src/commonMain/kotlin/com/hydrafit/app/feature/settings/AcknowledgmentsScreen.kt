package com.hydrafit.app.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import hydrafit.feature.settings.generated.resources.settings_licenses_open
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
    onOpenLicenses: () -> Unit,
    viewModel: AcknowledgmentsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AcknowledgmentsScreen(state = state, onBack = onBack, onOpenLicenses = onOpenLicenses)
}

@Composable
fun AcknowledgmentsScreen(
    state: AcknowledgmentsUiState,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
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
        AboutHeader(
            versionName = state.versionName,
            stacks = aboutHeaderStacks(LocalDensity.current.fontScale)
        )
        TextButton(onClick = onOpenLicenses) {
            Text(stringResource(Res.string.settings_licenses_open))
        }
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

internal fun aboutHeaderStacks(fontScale: Float): Boolean = fontScale >= 1.5f

@Composable
private fun AboutHeader(versionName: String, stacks: Boolean) {
    if (stacks) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HydraMark()
            AboutHeaderTexts(versionName)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HydraMark()
            AboutHeaderTexts(versionName)
        }
    }
}

@Composable
private fun AboutHeaderTexts(versionName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(Res.string.acknowledgments_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(Res.string.acknowledgments_version, versionName),
            style = MaterialTheme.typography.bodyMedium
        )
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

/** The selected 02B Hydra-H mark, drawn inline so About needs no extra bitmap resource. */
@Composable
private fun HydraMark() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(Color(0xFF1B2528), RoundedCornerShape(16.dp))
            .padding(10.dp)
            .clearAndSetSemantics { }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawPath(hydraMarkPath(size.width, size.height), Color(0xFFC2DEDA))
        }
    }
}

/** Uniform placement of the canonical 74x68 mark inside an arbitrary square/canvas. */
internal data class MarkGeometry(val scale: Float, val offsetX: Float, val offsetY: Float)

internal fun markGeometry(width: Float, height: Float): MarkGeometry {
    val scale = minOf(width / 74f, height / 68f)
    return MarkGeometry(
        scale = scale,
        offsetX = (width - 74f * scale) / 2f,
        offsetY = (height - 68f * scale) / 2f
    )
}

/** The canonical Mark path, mapped uniformly from its 27,17 74x68 viewBox and scaleY(0.86). */
private fun hydraMarkPath(width: Float, height: Float): Path {
    val geometry = markGeometry(width, height)
    fun x(value: Float) = geometry.offsetX + (value - 27f) * geometry.scale
    fun y(value: Float) = geometry.offsetY + (value * 0.86f - 17f) * geometry.scale
    return Path().apply {
        moveTo(x(28f), y(97f))
        lineTo(x(34f), y(44f))
        lineTo(x(29f), y(40f))
        lineTo(x(29f), y(33f))
        lineTo(x(33f), y(29f))
        lineTo(x(44f), y(29f))
        lineTo(x(48f), y(33f))
        lineTo(x(48f), y(40f))
        lineTo(x(43f), y(44f))
        lineTo(x(45f), y(49f))
        lineTo(x(59f), y(49f))
        lineTo(x(59f), y(36f))
        lineTo(x(54f), y(32f))
        lineTo(x(54f), y(25f))
        lineTo(x(58f), y(21f))
        lineTo(x(70f), y(21f))
        lineTo(x(74f), y(25f))
        lineTo(x(74f), y(32f))
        lineTo(x(69f), y(36f))
        lineTo(x(69f), y(49f))
        lineTo(x(83f), y(49f))
        lineTo(x(85f), y(44f))
        lineTo(x(80f), y(40f))
        lineTo(x(80f), y(33f))
        lineTo(x(84f), y(29f))
        lineTo(x(95f), y(29f))
        lineTo(x(99f), y(33f))
        lineTo(x(99f), y(40f))
        lineTo(x(94f), y(44f))
        lineTo(x(100f), y(97f))
        lineTo(x(81f), y(97f))
        lineTo(x(80f), y(61f))
        lineTo(x(48f), y(61f))
        lineTo(x(47f), y(97f))
        close()
    }
}
