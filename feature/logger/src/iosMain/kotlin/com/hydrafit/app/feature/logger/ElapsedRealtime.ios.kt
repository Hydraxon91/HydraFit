package com.hydrafit.app.feature.logger

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.timespec

@OptIn(ExperimentalForeignApi::class)
actual fun elapsedRealtimeMillis(): Long = memScoped {
    val current = alloc<timespec>()
    check(clock_gettime(CLOCK_MONOTONIC.toUInt(), current.ptr) == 0)
    current.tv_sec.toLong() * 1_000L + current.tv_nsec.toLong() / 1_000_000L
}
