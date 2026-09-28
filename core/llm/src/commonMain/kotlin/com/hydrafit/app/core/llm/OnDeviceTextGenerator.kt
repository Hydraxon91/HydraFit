package com.hydrafit.app.core.llm

interface OnDeviceTextGenerator {
    fun isAvailable(): Boolean

    /**
     * Generates text for [prompt]. When [jsonSchema] is provided the runtime is asked to
     * constrain the reply to that JSON shape, which keeps small models structurally sound.
     */
    fun generate(prompt: String, jsonSchema: String? = null): String
}
