package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.workout.RestCountdown
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A workout-scoped pair identity, independent of database slot ids and performed-set identity. */
@Serializable
data class Superset(val key: String, val transitionSeconds: Int, val pairRestSeconds: Int) {
    fun requireValid() {
        require(key.isNotBlank() && key.length <= 100) { "A superset needs a bounded pair key" }
        require(transitionSeconds in 0..MAX_REST_SECONDS) { "Invalid superset transition" }
        require(pairRestSeconds in 1..MAX_REST_SECONDS) { "Invalid superset pair rest" }
    }

    companion object {
        val MAX_REST_SECONDS: Int = (RestCountdown.MAX_DURATION_MILLIS / 1000).toInt()
    }
}

/** Both members carry identical metadata; malformed or overlapping groups are never repaired. */
fun validateSupersets(entries: List<Pair<String, Superset?>>) {
    val groups = entries.withIndex().filter { it.value.second != null }
        .groupBy { requireNotNull(it.value.second).key }
    groups.values.forEach { members ->
        require(members.size == 2) { "A superset needs exactly two entries" }
        val first = members[0]
        val second = members[1]
        require(first.index + 1 == second.index) { "Superset entries must be adjacent" }
        require(first.value.first != second.value.first) { "Superset exercises must be distinct" }
        val configuration = requireNotNull(first.value.second)
        configuration.requireValid()
        require(configuration == second.value.second) {
            "Superset members must share rest settings"
        }
    }
}

/** Strict persisted encoding. A missing value means unpaired, never inferred from exercise order. */
object SupersetJson {
    private val json = Json { encodeDefaults = true }

    fun encode(value: Superset): String {
        value.requireValid()
        return json.encodeToString(Superset.serializer(), value)
    }

    fun decode(text: String): Superset =
        json.decodeFromString(Superset.serializer(), text).also { it.requireValid() }
}
