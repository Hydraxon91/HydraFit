package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress

interface OnDeviceTextGenerator {
    fun isAvailable(): Boolean

    /**
     * Generates text for [prompt]. When [jsonSchema] is provided the runtime is asked to
     * constrain the reply to that JSON shape, which keeps small models structurally sound.
     * [onProgress] is called as tokens stream in so the UI can show live progress.
     */
    fun generate(
        prompt: String,
        jsonSchema: String? = null,
        onProgress: (OnDevicePlanProgress) -> Unit = {}
    ): String

    /**
     * Releases any cached native engine/resources. Safe to call at any time and idempotent; the next
     * [generate] re-initializes lazily. Callers use it to stop the engine after a failure and when the
     * on-device engine is no longer selected or its model is removed. Platforms without an engine
     * keep the default no-op.
     */
    fun release() {}
}
