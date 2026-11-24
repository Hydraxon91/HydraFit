package com.hydrafit.app.core.domain.engine

/**
 * How long a generated session may run, expressed as the exercises a day can hold. It is a cap, not
 * a time promise: the planner still adds only the work the weekly volume and floors call for.
 * [STANDARD] keeps the long-standing 6 and 8 exercise caps; [LONGER] allows 9 and 11 for people who
 * would rather stay in the gym longer than skip volume. Stored by [name].
 */
enum class SessionLength {
    STANDARD,
    LONGER;

    companion object {
        val DEFAULT: SessionLength = STANDARD

        /** The stored value's length, or [DEFAULT] when it is missing or no longer known. */
        fun fromStored(value: String?): SessionLength =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
