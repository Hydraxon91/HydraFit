package com.hydrafit.app.feature.logger

import android.os.SystemClock

actual fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
