package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress

class UnsupportedOnDeviceTextGenerator : OnDeviceTextGenerator {
    override fun isAvailable(): Boolean = false

    override fun generate(
        prompt: String,
        jsonSchema: String?,
        onProgress: (OnDevicePlanProgress) -> Unit
    ): String = error("On-device LLM inference is not supported on this platform")
}
