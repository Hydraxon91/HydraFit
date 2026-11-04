package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the fatigue screen's load states: an uncomputed map must not read as "no fatigue", and a
 * failed read must offer a retry rather than an unlabelled partial grid.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FatigueScreenStateTest {

    @Test
    fun loadingShowsProgressInsteadOfAnEmptyGrid() = runComposeUiTest {
        setContent {
            FatigueHeatmapScreen(state = FatigueHeatmapUiState(isLoading = true))
        }

        onNodeWithText("Loading your recorded training…").assertIsDisplayed()
        onNodeWithText("Body map").assertDoesNotExist()
    }

    @Test
    fun failedReadShowsTheErrorAndRetries() = runComposeUiTest {
        var retries = 0
        setContent {
            FatigueHeatmapScreen(
                state = FatigueHeatmapUiState(isLoading = false, loadFailed = true),
                onRetry = { retries += 1 }
            )
        }

        onNodeWithText(FAILURE, substring = true).assertIsDisplayed()
        onNodeWithText("Body map").assertDoesNotExist()
        onNodeWithText("Retry").performClick()

        assertTrue(retries == 1, "the retry action must reach the caller")
    }

    @Test
    fun bodyMapIsOfferedOnlyForAComputedMap() = runComposeUiTest {
        setContent {
            FatigueHeatmapScreen(
                state = FatigueHeatmapUiState(
                    isLoading = false,
                    entries = listOf(MuscleFatigueEntry(MuscleGroup.CHEST_UPPER, 0.5))
                )
            )
        }

        onNodeWithText("Body map").assertIsDisplayed()
    }

    @Test
    fun computedMapShowsRowsWithoutAProgressOrFailureMessage() = runComposeUiTest {
        setContent {
            FatigueHeatmapScreen(
                state = FatigueHeatmapUiState(
                    isLoading = false,
                    coverageComputed = true,
                    entries = listOf(MuscleFatigueEntry(MuscleGroup.CHEST_UPPER, 0.5))
                )
            )
        }

        onNodeWithText("Upper chest").assertIsDisplayed()
        onNodeWithText(FAILURE, substring = true).assertDoesNotExist()
        onNodeWithText("Loading your recorded training…").assertDoesNotExist()
    }
}

private const val FAILURE = "Could not read your training history"
