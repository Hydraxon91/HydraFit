package com.hydrafit.app.core.llm

/** Why the on-device planner stopped generating and used the built-in plan. */
enum class OnDevicePlannerFallback {
    /** The model needed more memory than the device could give it. */
    OUT_OF_MEMORY,

    /** Anything else: a native/JNI error, a template error, or unparsable output. */
    UNEXPECTED_FAILURE
}

/**
 * Reports why the on-device planner fell back, so an expected resource limit stays quiet
 * while a real defect is visible.
 */
fun interface OnDevicePlannerLogger {
    fun onFallback(reason: OnDevicePlannerFallback, cause: Throwable)
}

object NoopOnDevicePlannerLogger : OnDevicePlannerLogger {
    override fun onFallback(reason: OnDevicePlannerFallback, cause: Throwable) = Unit
}
