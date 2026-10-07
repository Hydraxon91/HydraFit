package com.hydrafit.app.core.domain.routine

/**
 * Hard-deletes a template and its workouts/slots. Returns false when the id does not exist.
 *
 * A template referenced by an activation cannot be deleted: the activation snapshots the template
 * but the reference is provenance the user can still see, so it must be archived instead.
 */
class DeleteRoutineTemplateUseCase(private val repository: RoutineTemplateRepository) {
    suspend operator fun invoke(id: Long): Boolean {
        if (repository.get(id) == null) return false
        if (repository.isReferencedByActivation(id)) {
            throw RoutineTemplateException(
                "This routine is used by a training block and can only be archived"
            )
        }
        repository.delete(id)
        return true
    }
}
