package com.hydrafit.app.core.domain.routine

import kotlinx.coroutines.flow.Flow

/** Streams every routine template (archived included, flagged by [RoutineTemplate.isArchived]). */
class ObserveRoutineTemplatesUseCase(private val repository: RoutineTemplateRepository) {
    operator fun invoke(): Flow<List<RoutineTemplate>> = repository.observeAll()
}
