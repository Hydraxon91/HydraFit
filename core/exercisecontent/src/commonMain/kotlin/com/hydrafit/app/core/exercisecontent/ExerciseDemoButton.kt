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
 * A one-tap shortcut to the exercise's demo: its hand-picked video when it has one, otherwise a
 * YouTube search for [exerciseName] (the same link the Instructions dialog falls back to). It
 * renders nothing only for a blank name. Nothing is requested until it is tapped.
 */
@Composable
fun ExerciseDemoButton(exerciseId: String?, exerciseName: String, modifier: Modifier = Modifier) {
    val url = ExerciseDemoLink.urlFor(exerciseId, exerciseName) ?: return
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
