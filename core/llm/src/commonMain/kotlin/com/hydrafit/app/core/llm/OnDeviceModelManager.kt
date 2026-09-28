package com.hydrafit.app.core.llm

interface OnDeviceModelManager {
    fun isInstalled(): Boolean

    fun remove()
}
