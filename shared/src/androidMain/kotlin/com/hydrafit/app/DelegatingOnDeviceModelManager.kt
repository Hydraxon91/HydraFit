package com.hydrafit.app

import com.hydrafit.app.core.llm.InsufficientStorageException
import com.hydrafit.app.core.llm.ModelSourceUnreadableException
import com.hydrafit.app.core.userdata.llm.ModelUpdateResult
import com.hydrafit.app.core.userdata.llm.OnDeviceModelManager
import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget

/**
 * Adapts a platform model manager (constructed in platform DI) to the neutral
 * [OnDeviceModelManager] port, mapping failures to a reason.
 */
class DelegatingOnDeviceModelManager(
    private val installedCheck: () -> Boolean,
    private val targetCheck: () -> OnDeviceModelTarget,
    private val onInstall: (String) -> Unit,
    private val onRemove: () -> Unit
) : OnDeviceModelManager {

    override fun isInstalled(): Boolean = installedCheck()

    override fun modelTarget(): OnDeviceModelTarget = targetCheck()

    override fun installFrom(source: String): ModelUpdateResult =
        runCatching { onInstall(source) }.toUpdateResult()

    override fun remove(): ModelUpdateResult = runCatching { onRemove() }.toUpdateResult()

    private fun Result<Unit>.toUpdateResult(): ModelUpdateResult = fold(
        onSuccess = { ModelUpdateResult.SUCCESS },
        onFailure = { failure ->
            when (failure) {
                is InsufficientStorageException -> ModelUpdateResult.INSUFFICIENT_STORAGE
                is ModelSourceUnreadableException -> ModelUpdateResult.UNREADABLE_SOURCE
                else -> ModelUpdateResult.FAILED
            }
        }
    )
}
