package com.hydrafit.app.feature.onboarding

import androidx.compose.ui.Modifier

/**
 * Stable external identities for the setup controls. Android exposes each tag as a resource id
 * ([onboardingTestTagsAsResourceId]) so UI automation can target a control by identity instead of
 * its position or a translated label. Tags are technical metadata: they never carry user-facing copy
 * and are never announced as accessibility descriptions.
 */
internal object OnboardingTestTags {
    /** The action bar holding Back / Next / Skip / Close. */
    const val ACTION_BAR = "onboarding_action_bar"

    /** One test tag per step's control group; a group is absent when its step is not shown. */
    const val UNITS = "onboarding_units"
    const val EQUIPMENT = "onboarding_equipment"
    const val GOAL = "onboarding_goal"
    const val WEEKDAYS = "onboarding_weekdays"
    const val PERSONAL_RECORDS = "onboarding_personal_records"

    /** The three first-workout handoffs. Each completes setup before its tab opens. */
    const val HANDOFF_GENERATE_PLAN = "onboarding_handoff_generate_plan"
    const val HANDOFF_BUILD_ROUTINE = "onboarding_handoff_build_routine"
    const val HANDOFF_LOG_WITHOUT_PLAN = "onboarding_handoff_log_without_plan"
}

/** Enables [OnboardingTestTags] as Android resource ids for the wrapped subtree; a no-op elsewhere. */
internal expect fun Modifier.onboardingTestTagsAsResourceId(): Modifier
