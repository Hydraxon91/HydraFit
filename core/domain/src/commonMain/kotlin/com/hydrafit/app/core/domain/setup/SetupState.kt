package com.hydrafit.app.core.domain.setup

import com.hydrafit.app.core.domain.time.DayOfWeek

const val SETUP_VERSION: Int = 1

enum class SetupDisposition { PENDING, COMPLETED, SKIPPED, RETURNING }

enum class SetupStep { WELCOME, UNITS, EQUIPMENT, GOAL, WEEKDAYS, PERSONAL_RECORDS, FIRST_WORKOUT }

/** The committed resume boundary, not an editor draft or an onboarding re-prompt policy. */
data class SetupState(
    val version: Int = SETUP_VERSION,
    val disposition: SetupDisposition = SetupDisposition.PENDING,
    val step: SetupStep = SetupStep.WELCOME,
    val revision: Long = 0L
) {
    init {
        require(version == SETUP_VERSION)
        require(revision >= 0L)
    }
}

/** Null means undecided; an explicitly empty or one-/seven-day schedule is not supported. */
fun requirePreferredWeekdays(weekdays: Set<DayOfWeek>?) {
    require(weekdays == null || weekdays.size in 2..6)
}
