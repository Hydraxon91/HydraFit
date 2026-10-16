package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the loading behaviour that the emulator smoke could not assert: the figure area keeps its
 * fixed height while a load is pending, the previous figure stays on screen until its replacement is
 * ready, and the text below the map does not move.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FatigueMapLoadingTest {

    @Test
    fun figureAreaKeepsItsHeightWhileTheFirstLoadIsPending() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        setContent {
            FatigueMapSection(
                state = FatigueHeatmapUiState(),
                loadArtwork = { _, _ ->
                    gate.await()
                    artworkFromDisk(BodyModel.MALE, FatigueMapView.FRONT)
                }
            )
        }
        waitForIdle()
        val noteBefore = schematicNoteTop()
        gate.complete(Unit)
        waitUntil {
            onAllNodesWithContentDescription(
                MALE_FRONT_DESCRIPTION
            ).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithContentDescription(MALE_FRONT_DESCRIPTION).assertHeightIsEqualTo(260.dp)
        assertEquals(noteBefore, schematicNoteTop())
    }

    @Test
    fun keepsThePreviousFigureWhileTheReplacementLoads() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        var holdNextLoad = false
        setContent {
            FatigueMapSection(
                state = FatigueHeatmapUiState(),
                loadArtwork = { model, view ->
                    if (holdNextLoad) gate.await()
                    artworkFromDisk(model, view)
                }
            )
        }
        waitUntil {
            onAllNodesWithContentDescription(
                MALE_FRONT_DESCRIPTION
            ).fetchSemanticsNodes().isNotEmpty()
        }
        val noteBefore = schematicNoteTop()

        holdNextLoad = true
        onNodeWithText("Back").performClick()
        waitForIdle()

        // The requested figure is still loading, so the front figure and the layout stay put.
        onNodeWithContentDescription(MALE_FRONT_DESCRIPTION).assertHeightIsEqualTo(260.dp)
        assertEquals(noteBefore, schematicNoteTop())

        gate.complete(Unit)
        waitUntil {
            onAllNodesWithContentDescription(
                MALE_BACK_DESCRIPTION
            ).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(noteBefore, schematicNoteTop())
    }
}

private const val MALE_FRONT_DESCRIPTION = "Male, Front — estimated muscle fatigue"
private const val MALE_BACK_DESCRIPTION = "Male, Back — estimated muscle fatigue"

private val bodymapDirectory = File("src/commonMain/composeResources/files/bodymap")

private fun artworkFromDisk(model: BodyModel, view: FatigueMapView): AnatomicalArtwork {
    val filename = artworkResource(model, view).substringAfterLast('/')
    return buildAnatomicalArtwork(
        parseArtworkSource(File(bodymapDirectory, filename).readText()),
        model,
        view
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.schematicNoteTop() =
    onNodeWithText("Schematic anatomy", substring = true).getBoundsInRoot().top
