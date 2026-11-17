package com.hydrafit.app.core.userdata.settings

// The iOS target is not planned to ship, so no Keychain-backed store exists. Replace this with one
// only if iOS is revived.
class NoopApiKeyStore : ApiKeyStore {
    override fun load(): String? = null

    override fun save(apiKey: String) = Unit

    override fun clear() = Unit
}
