package com.hydrafit.app.core.domain.routine

import kotlinx.coroutines.flow.Flow

/**
 * Groups the routine-template operations a UI performs so a ViewModel depends on one collaborator.
 * Pure delegation only: it adds no behavior beyond the use cases it forwards to.
 */
class RoutineTemplateActions(
    private val observeTemplates: ObserveRoutineTemplatesUseCase,
    private val saveTemplate: SaveRoutineTemplateUseCase,
    private val duplicateTemplate: DuplicateRoutineTemplateUseCase,
    private val archiveTemplate: ArchiveRoutineTemplateUseCase,
    private val deleteTemplate: DeleteRoutineTemplateUseCase
) {
    fun observe(): Flow<List<RoutineTemplate>> = observeTemplates()

    /** Creates or updates a template; returns the persisted form (with its id/revision). */
    suspend fun save(draft: RoutineTemplate): RoutineTemplate = saveTemplate(draft)

    /** Copies a template to fresh ids; returns the new id, or null if the source is gone. */
    suspend fun duplicate(id: Long, name: String? = null): Long? = duplicateTemplate(id, name)

    suspend fun setArchived(id: Long, archived: Boolean) = archiveTemplate(id, archived)

    /** Hard-deletes an unreferenced template; throws [RoutineTemplateException] if it is in use. */
    suspend fun delete(id: Long): Boolean = deleteTemplate(id)
}
