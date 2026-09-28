package com.hydrafit.app.core.llm

interface OnDeviceTextGenerator {
    fun isAvailable(): Boolean

    fun generate(prompt: String): String
}
