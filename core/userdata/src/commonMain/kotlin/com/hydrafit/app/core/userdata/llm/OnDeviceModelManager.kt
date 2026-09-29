package com.hydrafit.app.core.userdata.llm

/**
 * Platform-neutral entry point for managing the on-device LLM model file.
 *
 * [installFrom] takes an opaque platform handle for the picked file. On Android
 * this is a `content://` URI string; other platforms may interpret it differently.
 */
interface OnDeviceModelManager {
    fun isInstalled(): Boolean

    /**
     * The hardware the installed model was built for, so the UI can show what is loaded and the
     * engine can order its backends. Defaults to [OnDeviceModelTarget.CPU_GPU] when unknown.
     */
    fun modelTarget(): OnDeviceModelTarget = OnDeviceModelTarget.CPU_GPU

    fun installFrom(source: String): ModelUpdateResult

    fun remove(): ModelUpdateResult
}
