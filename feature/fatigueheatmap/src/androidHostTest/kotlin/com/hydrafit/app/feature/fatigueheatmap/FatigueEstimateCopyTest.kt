package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the fatigue-meter framing: the screen must present its values as an estimate and keep the
 * "not a measurement" caveat permanently visible, so a low or zero score is never read as proof of
 * recovery or as a complete picture of recorded involvement.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FatigueEstimateCopyTest {

    @Test
    fun screenLabelsTheScoresAsAnEstimateBelowTheTitle() = runComposeUiTest {
        setContent { FatigueHeatmapScreen(state = FatigueHeatmapUiState()) }

        onNodeWithText(TITLE).assertIsDisplayed()
        onNodeWithText(CAVEAT, substring = true).assertIsDisplayed()

        val titleBottom = onNodeWithText(TITLE).getBoundsInRoot().bottom
        val caveatTop = onNodeWithText(CAVEAT, substring = true).getBoundsInRoot().top
        assertTrue(
            caveatTop >= titleBottom,
            "the estimate caveat must sit below the screen title"
        )
    }

    @Test
    fun bodyMapLegendReadsAsAnEstimateToo() = runComposeUiTest {
        setContent { FatigueHeatmapScreen(state = FatigueHeatmapUiState()) }

        onNodeWithText("Body map").performClick()
        waitForIdle()

        onNodeWithText("Low estimated fatigue").performScrollTo().assertIsDisplayed()
        onNodeWithText("High estimated fatigue").assertIsDisplayed()
    }

    @Test
    fun resourcesKeepTheEstimateFramingAndTheRecoveryCaveat() {
        val strings = File("src/commonMain/composeResources/values/strings.xml").readText()
        assertDeclares(strings, "fatigue_heatmap_title", TITLE)
        assertDeclares(strings, "fatigue_map_legend_low", "Low estimated fatigue")
        assertDeclares(strings, "fatigue_map_legend_high", "High estimated fatigue")
        val note = NOTE_RESOURCE.find(strings)?.groupValues?.get(1)
        assertTrue(note != null, "fatigue_estimate_note must be declared")
        assertTrue(
            note.contains("not a measurement"),
            "the note must deny being a measurement"
        )
        assertTrue(
            note.contains("do not confirm recovery"),
            "the note must deny that low values show recovery"
        )
        assertTrue(
            note.contains("may be incomplete"),
            "the note must allow for incomplete recorded mappings"
        )
    }
}

private fun assertDeclares(strings: String, name: String, value: String) {
    assertTrue(
        strings.contains("<string name=\"$name\">$value</string>"),
        "resource $name must keep its text: $value"
    )
}

private const val TITLE = "Estimated muscle fatigue"
private const val CAVEAT = "Estimated from recorded working sets."
private val NOTE_RESOURCE = Regex("<string name=\"fatigue_estimate_note\">(.*?)</string>")
