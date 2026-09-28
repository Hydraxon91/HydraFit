package com.hydrafit.app.core.userdata.llm

/**
 * Platform-neutral entry point for managing the on-device LLM model file.
 *
 * [installFrom] takes an opaque platform handle for the picked file. On Android
 * this is a `content://` URI string; other platforms may interpret it differently.
 * Both mutations return whether they succeeded so callers can surface failures.
 */
interface OnDeviceModelManager {
    fun isInstalled(): Boolean

    fun installFrom(source: String): Boolean

    fun remove(): Boolean
}
