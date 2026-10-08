package com.hydrafit.app.core.domain.engine

/** Persistent distinction between legacy absence and a deliberately invalidated assessment. */
enum class VolumeExplanationStatus {
    ABSENT,
    AVAILABLE,
    INVALIDATED_BY_SUBSTITUTION
}
