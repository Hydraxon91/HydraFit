package com.hydrafit.app.feature.logger

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_rest_complete
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Root-mounted feature presentation so changing tabs does not hide a live foreground completion. */
@Composable
fun RestCompletionPresenter(modifier: Modifier = Modifier) {
    val completion: RestCompletionPresentation = koinInject()
    val owner = LocalLifecycleOwner.current
    val host = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val message = stringResource(Res.string.logger_rest_complete)
    DisposableEffect(owner, completion) {
        completion.setVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ ->
            completion.setVisible(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            completion.setVisible(false)
        }
    }
    LaunchedEffect(owner, completion, message, haptic) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            completion.completions.collect {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                host.showSnackbar(message)
            }
        }
    }
    SnackbarHost(hostState = host, modifier = modifier)
}
