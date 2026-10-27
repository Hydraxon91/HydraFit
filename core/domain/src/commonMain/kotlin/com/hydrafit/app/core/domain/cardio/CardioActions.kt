package com.hydrafit.app.core.domain.cardio

/**
 * Creates and edits on-device identities.
 *
 * A random identity avoids needing a platform UUID in `commonMain`; the value only has to be stable
 * and collision-resistant enough to deduplicate a merge, never globally meaningful.
 */
object CardioIdentity {
    fun new(): String {
        val high = kotlin.random.Random.nextLong()
        val low = kotlin.random.Random.nextLong()
        return high.toULong().toString(16).padStart(16, '0') +
            low.toULong().toString(16).padStart(16, '0')
    }
}

/** Validates and stores a new manual activity. */
class AddCardioActivityUseCase(private val repository: CardioRepository) {
    suspend operator fun invoke(activity: CardioActivity): Long {
        val new = activity.copy(
            id = 0,
            identity = activity.identity.ifBlank {
                CardioIdentity.new()
            }
        )
        new.requireValid()
        return repository.add(new)
    }
}

/** Replaces the recorded values of an existing activity; its identity and id are preserved. */
class EditCardioActivityUseCase(private val repository: CardioRepository) {
    suspend operator fun invoke(activity: CardioActivity) {
        require(activity.id > 0) { "Editing an activity needs its stored id" }
        activity.requireValid()
        repository.update(activity)
    }
}

class DeleteCardioActivityUseCase(private val repository: CardioRepository) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
