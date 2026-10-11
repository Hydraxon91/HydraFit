package com.hydrafit.app.core.domain.time

/** Returns a stable identity for the current OS boot, or null when it cannot be established. */
fun interface BootIdentityProvider {
    fun currentBootIdentity(): String?
}
