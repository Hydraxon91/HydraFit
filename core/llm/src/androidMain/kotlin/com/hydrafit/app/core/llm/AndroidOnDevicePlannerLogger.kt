package com.hydrafit.app.core.llm

import android.util.Log

class AndroidOnDevicePlannerLogger : OnDevicePlannerLogger {
    override fun onFallback(reason: OnDevicePlannerFallback, cause: Throwable) {
        when (reason) {
            OnDevicePlannerFallback.OUT_OF_MEMORY -> Log.w(
                TAG,
                "On-device planner ran out of memory; using the built-in plan",
                cause
            )
            OnDevicePlannerFallback.UNEXPECTED_FAILURE -> Log.e(
                TAG,
                "On-device planner failed unexpectedly; using the built-in plan",
                cause
            )
        }
    }

    private companion object {
        const val TAG = "OnDevicePlanner"
    }
}
