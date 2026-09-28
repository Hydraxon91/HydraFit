package com.hydrafit.app.core.domain.time

fun interface TimeProvider {
    fun nowMillis(): Long
}
