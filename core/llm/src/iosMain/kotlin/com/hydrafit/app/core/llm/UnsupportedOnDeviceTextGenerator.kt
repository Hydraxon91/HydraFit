package com.hydrafit.app.core.llm

class UnsupportedOnDeviceTextGenerator : OnDeviceTextGenerator {
    override fun isAvailable(): Boolean = false

    override fun generate(prompt: String, jsonSchema: String?): String =
        error("On-device LLM inference is not supported on this platform")
}
