package com.hydrafit.app.core.domain.routine

/**
 * Hard-deletes a template and its workouts/slots. Returns false when the id does not exist.
 *
 * Templates referenced by an activation cannot be deleted (an activation snapshots the template,
 * so this guard is enforced by the scheduling layer once activations exist).
 */
class DeleteRoutineTemplateUseCase(private val repository: RoutineTemplateRepository) {
    suspend operator fun invoke(id: Long): Boolean {
        if (repository.get(id) == null) return false
        repository.delete(id)
        return true
    }
}
