package com.hydrafit.app.core.userdata.llm

/**
 * Platform-neutral entry point for managing the on-device LLM model file.
 *
 * [installFrom] takes an opaque platform handle for the picked file. On Android
 * this is a `content://` URI string; other platforms may interpret it differently.
 */
interface OnDeviceModelManager {
    fun isInstalled(): Boolean

    fun installFrom(source: String): ModelUpdateResult

    fun remove(): ModelUpdateResult
}
