package com.hydrafit.app.feature.onboarding

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.onboardingTestTagsAsResourceId(): Modifier =
    semantics { testTagsAsResourceId = true }
