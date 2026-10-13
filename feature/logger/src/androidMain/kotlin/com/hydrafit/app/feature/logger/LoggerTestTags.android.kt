package com.hydrafit.app.feature.logger

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.loggerTestTagsAsResourceId(): Modifier =
    semantics { testTagsAsResourceId = true }
