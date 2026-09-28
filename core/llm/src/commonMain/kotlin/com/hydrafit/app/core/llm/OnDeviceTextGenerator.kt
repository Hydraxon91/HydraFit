package com.hydrafit.app.core.llm

const val ON_DEVICE_LLM_ASSET: String = "models/on_device_llm.litertlm"

interface OnDeviceTextGenerator {
    fun isAvailable(): Boolean

    fun generate(prompt: String): String
}
