package com.hydrafit.app.core.domain.backup

/**
 * The platform's background scheduling for automatic backups: one replaceable periodic job, armed
 * while automation is enabled and removed when it is turned off. Delivery cadence is best-effort;
 * the runner re-checks due-ness on every opportunity, so an early or late job changes nothing.
 */
interface AutomaticBackupScheduler {
    /** Registers (or replaces) the periodic job; idempotent for the same job identity. */
    fun schedule()

    /** Removes the periodic job; safe when none is registered. */
    fun cancel()
}

/** Fallback for platforms without a background scheduler (iOS in 0.9.3): every call is a no-op. */
class UnsupportedAutomaticBackupScheduler : AutomaticBackupScheduler {
    override fun schedule() = Unit

    override fun cancel() = Unit
}
