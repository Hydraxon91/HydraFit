package com.hydrafit.app

import com.hydrafit.app.core.userdata.llm.OnDeviceModelManager

/**
 * Adapts a platform model manager (constructed in platform DI) to the neutral
 * [OnDeviceModelManager] port and reports failures as a boolean instead of a crash.
 */
class DelegatingOnDeviceModelManager(
    private val installedCheck: () -> Boolean,
    private val onInstall: (String) -> Unit,
    private val onRemove: () -> Unit
) : OnDeviceModelManager {

    override fun isInstalled(): Boolean = installedCheck()

    override fun installFrom(source: String): Boolean = runCatching { onInstall(source) }.isSuccess

    override fun remove(): Boolean = runCatching { onRemove() }.isSuccess
}
