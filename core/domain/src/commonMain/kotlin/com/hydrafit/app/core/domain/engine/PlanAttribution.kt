package com.hydrafit.app.core.domain.engine

/**
 * Who produced an accepted plan, so its persisted volume explanation is never attributed to the
 * wrong source. A deterministic plan's coverage is a real calculation result; an
 * [AI_GENERATED] plan's coverage is an assessment of the sanitized model output, not a claim about
 * the model's reasoning.
 */
enum class PlanAttribution {
    DETERMINISTIC,
    AI_GENERATED
}
