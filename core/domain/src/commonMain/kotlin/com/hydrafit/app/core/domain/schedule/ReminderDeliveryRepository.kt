package com.hydrafit.app.core.domain.schedule

/**
 * Device-local bookkeeping for the one reminder posted per active block and local day.
 *
 * This is delivery state, not a portable preference: whether a reminder was posted is true only of
 * the device that posted it, so it is never backed up and is cleared on restore. The claim is what
 * keeps concurrent delivery paths to at most one post.
 */
interface ReminderDeliveryRepository {
    /** True when a reminder for this block and local day has already been claimed. */
    suspend fun isDelivered(activationId: Long, reminderEpochDay: Long): Boolean

    /**
     * Atomically claims this block and local day, returning false when another delivery already
     * claimed it. A caller posts only after winning the claim.
     */
    suspend fun claim(activationId: Long, reminderEpochDay: Long, deliveredAtMillis: Long): Boolean

    /** Gives a claim back after a failed post, so a later pass inside the lateness window can retry. */
    suspend fun release(activationId: Long, reminderEpochDay: Long)
}
