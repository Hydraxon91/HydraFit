package com.hydrafit.app.core.domain.engine

/**
 * Signals that a planner engine could not produce a plan.
 *
 * [transient] is true when the provider reported a temporary condition
 * (for example an overloaded Gemini service) so the UI can suggest retrying.
 */
class PlanGenerationException(val transient: Boolean, message: String) : Exception(message)
