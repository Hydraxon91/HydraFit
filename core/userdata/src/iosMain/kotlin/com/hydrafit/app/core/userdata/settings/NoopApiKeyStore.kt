package com.hydrafit.app.core.userdata.settings

// iOS in-app key entry is deferred until the iOS app is wired up; the build-time
// environment key is still used via ApiKeyProvider. Replace with a Keychain-backed
// implementation when the iOS target ships.
class NoopApiKeyStore : ApiKeyStore {
    override fun load(): String? = null

    override fun save(apiKey: String) = Unit

    override fun clear() = Unit
}
