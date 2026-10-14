package com.hydrafit.app.core.domain.time

fun interface TimeProvider {
    fun nowMillis(): Long

    /** The local time zone's UTC offset (ms) at the current instant; 0 means UTC. */
    fun utcOffsetMillis(): Long = 0L

    /**
     * The UTC instant for [minuteOfDay] (minutes past local midnight) on the local civil day
     * [localEpochDay].
     *
     * The default applies the single current [utcOffsetMillis], so a local time a DST transition
     * skips or repeats maps to that fixed-offset instant, the same way [localCivilToEpochMillis]
     * behaves. A platform that can resolve its current zone overrides this so a skipped local time
     * resolves forward to the first valid instant; the caller still suppresses a local day it has
     * already delivered, so a repeated local time never produces a second decision.
     */
    fun epochMillisForLocalMinute(localEpochDay: Long, minuteOfDay: Int): Long =
        localMinuteToEpochMillisUtc(localEpochDay, minuteOfDay, utcOffsetMillis())
}
