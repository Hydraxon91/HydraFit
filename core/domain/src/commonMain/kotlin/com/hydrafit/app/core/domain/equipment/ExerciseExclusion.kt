package com.hydrafit.app.core.domain.equipment

/**
 * A user-set exclusion that removes an exercise from generated plans and from ranked substitution
 * candidates. It is a hard gate, distinct from the soft [ExercisePreference] (Prefer-less is not
 * exclusion). [expiresAtMillis] is an absolute UTC timestamp, or null for an indefinite exclusion.
 *
 * An expired exclusion stops filtering but is never deleted automatically: it stays visible so the
 * user can re-enable or remove it, and re-excluding resets the window. An exclusion is never
 * inferred, carries no medical reason, and never rewrites an accepted plan, frozen activation,
 * occurrence prescription or recorded set.
 */
data class ExerciseExclusion(val exerciseId: String, val expiresAtMillis: Long? = null) {
    /** Active exclusions gate generation; an expired (or absent) one does not. */
    fun isActive(nowMillis: Long): Boolean = expiresAtMillis == null || nowMillis < expiresAtMillis

    companion object {
        /** Product default exclusion window: 12 weeks. Not a physiological or research interval. */
        const val DEFAULT_WINDOW_DAYS = 84L

        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        /** Absolute expiry for a default-window exclusion set at [nowMillis]. */
        fun defaultExpiryFrom(nowMillis: Long): Long =
            nowMillis + DEFAULT_WINDOW_DAYS * MILLIS_PER_DAY
    }
}
