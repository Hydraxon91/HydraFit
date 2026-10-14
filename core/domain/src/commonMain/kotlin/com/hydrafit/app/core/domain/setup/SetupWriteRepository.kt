package com.hydrafit.app.core.domain.setup

/** The writer compares the complete expected state and commits the input and boundary atomically. */
interface SetupWriteRepository {
    suspend fun commit(command: SetupCommit): SetupState
}

data class SetupCommit(
    val expected: SetupState,
    val input: SetupStepInput?,
    val revisit: Boolean = false
) {
    fun nextState(): SetupState {
        require(revisit || expected.disposition == SetupDisposition.PENDING)
        require(expected.revision < Long.MAX_VALUE)
        input?.let(::requireSetupInput)
        val step = input?.step ?: expected.step
        val disposition = when {
            input == null -> SetupDisposition.SKIPPED
            step == SetupStep.FIRST_WORKOUT -> SetupDisposition.COMPLETED
            else -> expected.disposition
        }
        val nextStep = if (input != null && step != SetupStep.FIRST_WORKOUT) {
            SetupStep.entries[step.ordinal + 1]
        } else {
            step
        }
        return expected.copy(
            disposition = disposition,
            step = nextStep,
            revision = expected.revision + 1L
        )
    }
}

class StaleSetupContextException : IllegalStateException("Setup state changed before saving")
