package com.hydrafit.app.core.domain.equipment

/**
 * An explicit, user-set soft preference for one catalog exercise, keyed by exercise id.
 *
 * It is never inferred from substitutions, skips, soreness, busy equipment or passive acceptance,
 * and it never decays. [PREFER_LESS] never excludes an exercise: it only orders an otherwise-eligible
 * candidate below its neutral and preferred peers. Absence from the preference store means
 * [NEUTRAL]. A preference can never bypass a hard gate (equipment availability, EX-01 exclusion,
 * soreness skip/reduce or direct-arm coverage), and changing it never rewrites an accepted plan,
 * frozen activation, occurrence prescription or recorded set.
 */
enum class ExercisePreference {
    PREFER,
    NEUTRAL,
    PREFER_LESS
}
