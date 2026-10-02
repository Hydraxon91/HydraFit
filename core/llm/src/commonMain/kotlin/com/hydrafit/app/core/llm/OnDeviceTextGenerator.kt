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
}
