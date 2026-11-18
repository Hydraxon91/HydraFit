package com.hydrafit.app.core.exercisecontent

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_demo_button
import org.jetbrains.compose.resources.stringResource

/**
 * A one-tap shortcut to the exercise's hand-picked demo video. It renders nothing for an exercise
 * without one (including every custom exercise), so it never offers a generic search that the
 * Instructions dialog already provides. Nothing is requested until it is tapped.
 */
@Composable
fun ExerciseDemoButton(exerciseId: String?, modifier: Modifier = Modifier) {
    val url = ExerciseDemoLink.curatedUrl(exerciseId) ?: return
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openDemo(url) }, modifier = modifier) {
        Text(stringResource(Res.string.exercise_demo_button))
    }
}

internal fun UriHandler.openDemo(url: String) {
    // A device with no browser or YouTube app cannot open the link; the screen simply stays put
    // instead of crashing.
    try {
        openUri(url)
    } catch (_: IllegalArgumentException) {
        // Nothing can handle the link.
    }
}
