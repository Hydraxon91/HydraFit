package com.hydrafit.app.core.domain.time

fun interface TimeProvider {
    fun nowMillis(): Long

    /** The local time zone's UTC offset (ms) at the current instant; 0 means UTC. */
    fun utcOffsetMillis(): Long = 0L
}
