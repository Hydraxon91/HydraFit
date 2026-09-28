package com.hydrafit.app.core.userdata.settings

interface ApiKeyStore {
    fun load(): String?

    fun save(apiKey: String)

    fun clear()
}
