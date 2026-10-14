package com.hydrafit.app.core.domain.setup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveSetupStepUseCase(
    private val repository: SetupWriteRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend operator fun invoke(
        expected: SetupState,
        input: SetupStepInput,
        revisit: Boolean = false
    ): SetupState {
        val command = SetupCommit(expected, input.snapshot(), revisit)
        return withContext(dispatcher) {
            command.nextState()
            repository.commit(command)
        }
    }
}
