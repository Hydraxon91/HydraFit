package com.hydrafit.app.core.domain.equipment

enum class MovementPattern(val isCompound: Boolean) {
    HORIZONTAL_PUSH(true),
    VERTICAL_PUSH(true),
    HORIZONTAL_PULL(true),
    VERTICAL_PULL(true),
    SQUAT(true),
    HINGE(true),
    LUNGE(true),
    CALF_RAISE(false),
    CHEST_FLY(false),
    BICEPS_ISOLATION(false),
    TRICEPS_ISOLATION(false),
    SHOULDER_ISOLATION(false),
    LEG_ISOLATION(false),
    CORE(false)
}
