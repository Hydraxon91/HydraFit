package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.time.TimeProvider

/**
 * Archives or restores a template. An archived template is hidden from ordinary selection and
 * cannot be activated, but its stored workouts remain intact. Missing ids are a no-op.
 */
class ArchiveRoutineTemplateUseCase(
    private val repository: RoutineTemplateRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(id: Long, archived: Boolean) {
        if (repository.get(id) == null) return
        repository.setArchived(id, if (archived) timeProvider.nowMillis() else null)
    }
}
