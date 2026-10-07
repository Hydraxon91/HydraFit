package com.hydrafit.app.core.domain.routine

/**
 * Copies a stored template into a new, fully independent one. Workout and slot ids are cleared so
 * the repository inserts fresh rows, and the provenance/name can be overridden. Returns the new id,
 * or null when the source no longer exists.
 */
class DuplicateRoutineTemplateUseCase(
    private val repository: RoutineTemplateRepository,
    private val saveRoutineTemplate: SaveRoutineTemplateUseCase
) {
    suspend operator fun invoke(sourceId: Long, name: String? = null): Long? {
        val source = repository.get(sourceId) ?: return null
        val draft = RoutineTemplate(
            name = name ?: "${source.name} copy",
            sourcePlanId = source.sourcePlanId,
            workouts = source.workouts.sortedBy { it.position }.map { workout ->
                workout.copy(
                    id = 0,
                    entries = workout.entries.sortedBy { it.position }.map { it.copy(id = 0) }
                )
            }
        )
        return saveRoutineTemplate(draft).id
    }
}
