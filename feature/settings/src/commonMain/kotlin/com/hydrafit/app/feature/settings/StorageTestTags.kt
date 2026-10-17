package com.hydrafit.app.feature.settings

import androidx.compose.ui.Modifier

/**
 * Enables [StorageDataTestTags] as Android resource ids for the wrapped subtree so a UI-automation
 * tool can target a control by identity; a no-op on platforms without resource ids.
 */
internal expect fun Modifier.storageTestTagsAsResourceId(): Modifier
