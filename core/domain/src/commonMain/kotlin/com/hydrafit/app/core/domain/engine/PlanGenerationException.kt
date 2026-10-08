package com.hydrafit.app.core.domain.engine

/**
 * Why a planner engine could not produce a plan, so the UI can show a useful reason instead of a
 * generic failure. [PlanFailureReason.UNKNOWN] is the safe default for anything unmapped.
 */
enum class PlanFailureReason {
    /** The provider reported a rate limit (HTTP 429) that is expected to clear quickly. */
    RATE_LIMITED,

    /** The provider reported an exhausted quota (HTTP 429, daily/plan limit) — retrying won't help. */
    QUOTA_EXHAUSTED,

    /** The provider is temporarily unavailable (HTTP 5xx). */
    SERVICE_UNAVAILABLE,

    /** The request timed out. */
    TIMEOUT,

    /** The device could not reach the provider. */
    NETWORK,

    /** The API key is missing, invalid, or not permitted. */
    INVALID_API_KEY,

    /** The provider rejected the request itself (HTTP 4xx other than auth/rate). */
    INVALID_REQUEST,

    /** The provider returned a response the app could not use (unparseable or unusable). */
    INVALID_RESPONSE,

    /**
     * No exercise was eligible for the requested plan because of the user's exclusions (and/or
     * available equipment). Non-transient: the user must change exclusions or equipment, then retry.
     */
    NO_ELIGIBLE_EXERCISES,

    /** Eligible exercises exist, but generation selected no work; not an exclusion diagnosis. */
    NO_USABLE_EXERCISES,

    /** Anything not otherwise mapped. */
    UNKNOWN
}

/**
 * Signals that a planner engine could not produce a plan.
 *
 * [transient] is true when the provider reported a temporary condition
 * (for example an overloaded Gemini service) so the UI can suggest retrying.
 */
class PlanGenerationException(
    val transient: Boolean,
    val reason: PlanFailureReason = PlanFailureReason.UNKNOWN,
    message: String
) : Exception(message)
