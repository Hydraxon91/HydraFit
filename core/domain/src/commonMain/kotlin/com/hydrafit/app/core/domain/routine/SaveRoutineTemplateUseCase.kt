package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.time.TimeProvider

/**
 * Creates or updates a routine template. The draft is validated and normalized, the revision is
 * bumped on every edit, and `createdAtMillis`/archival/provenance are preserved from the stored
 * row so an edit never rewinds them. Returns the persisted template (with its id and revision).
 */
class SaveRoutineTemplateUseCase(
    private val repository: RoutineTemplateRepository,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(draft: RoutineTemplate): RoutineTemplate {
        val validated = RoutineTemplateValidator.validate(draft)
        val existing = if (draft.id != 0L) repository.get(draft.id) else null
        val now = timeProvider.nowMillis()
        val toSave = validated.copy(
            revision = (existing?.revision ?: 0) + 1,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
            archivedAtMillis = existing?.archivedAtMillis ?: validated.archivedAtMillis,
            sourcePlanId = existing?.sourcePlanId ?: validated.sourcePlanId
        )
        val id = repository.save(toSave)
        // Reload so the caller gets the persisted child ids (a new template's workouts/slots are
        // assigned by storage); fall back to the draft if the write is not yet visible.
        return repository.get(id) ?: toSave.copy(id = id)
    }
}
