package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Complete effective prescription for one guided occurrence set position. */
@Serializable
data class GuidedWorkoutTargetOverride(
    val setIndex: Int,
    val target: SetTarget,
    val cluster: ClusterPrescription? = null
) {
    fun requireValid(sets: Int, capability: ExerciseLoadCapability) {
        require(setIndex in 0 until sets) { "Guided target index is outside the occurrence entry" }
        target.requireValid()
        WorkoutLoadPolicy.validate(capability, target.loadKind, target.weightKg)
        require(cluster == null || target.reps != null) { "Clusters require repetitions" }
        if (cluster != null) {
            cluster.requireValid()
            require(target.loadKind == LoadKind.EXTERNAL) {
                "Clusters require external load"
            }
            require(
                target.reps == cluster.openingSegment.reps &&
                    target.weightKg == cluster.openingSegment.weightKg
            ) { "The opening target must match the cluster's opening segment" }
        }
    }
}

/** Nullable persisted collection: null means no guided edits have been made. */
@Serializable
data class GuidedWorkoutTargets(
    val version: Int = VERSION,
    val overrides: List<GuidedWorkoutTargetOverride>
) {
    fun requireValid(entry: OccurrenceEntry) {
        require(version == VERSION) { "Unsupported guided target version" }
        require(overrides.map { it.setIndex }.distinct().size == overrides.size) {
            "Guided target indexes must be unique"
        }
        overrides.forEach { it.requireValid(entry.sets, entry.loadCapability) }
        val base = originalTargets(entry)
        overrides.forEach { override ->
            val target = override.target
            override.cluster?.requireValid()
            requireSameMetric(base[override.setIndex], target)
            if (target.reps != null) {
                require(target.reps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS)
            }
            require(target.durationSeconds == null || target.durationSeconds > 0)
            requireLoadKindCompatible(entry.loadCapability, target.loadKind)
            require(
                target.distanceMeters == null ||
                    target.distanceMeters in
                    PrescriptionBounds.MIN_DISTANCE_METERS..PrescriptionBounds.MAX_DISTANCE_METERS
            )
            require(
                target.forceNewtons == null ||
                    target.forceNewtons in
                    PrescriptionBounds.MIN_FORCE_NEWTONS..PrescriptionBounds.MAX_FORCE_NEWTONS
            )
            require((override.cluster == null) == (base[override.setIndex].cluster == null)) {
                "A guided override cannot add or remove a cluster"
            }
            if (override.cluster != null) {
                require(base[override.setIndex].target.reps != null) {
                    "Cluster cannot replace non-rep work"
                }
                require(
                    override.cluster.segments.size ==
                        base[override.setIndex].cluster?.segments?.size
                ) {
                    "Cluster segment count cannot change"
                }
                require(override.cluster.kind == base[override.setIndex].cluster?.kind) {
                    "Cluster kind cannot change"
                }
                require(
                    override.cluster.restSeconds == base[override.setIndex].cluster?.restSeconds
                ) {
                    "Cluster rest cannot change"
                }
            }
        }
    }

    fun targetFor(entry: OccurrenceEntry, setIndex: Int): GuidedWorkoutTargetOverride {
        requireValid(entry)
        require(setIndex in 0 until entry.sets)
        return overrides.firstOrNull { it.setIndex == setIndex }
            ?: originalTargets(entry)[setIndex]
    }

    companion object {
        const val VERSION: Int = 1
        private val json = Json { encodeDefaults = true }

        fun encode(value: GuidedWorkoutTargets, entry: OccurrenceEntry): String {
            value.requireValid(entry)
            return json.encodeToString(serializer(), value)
        }

        fun decode(text: String, entry: OccurrenceEntry): GuidedWorkoutTargets =
            json.decodeFromString(serializer(), text).also { it.requireValid(entry) }
    }
}

/** User edits to only the named fields; null load is an explicit clear when LOAD is present. */
data class GuidedTargetPatch(
    val target: SetTarget,
    val fields: Set<GuidedTargetField>,
    val laterClusterSegments: Map<Int, ClusterSegmentPatch> = emptyMap()
)

enum class GuidedTargetField { REPS, DURATION_SECONDS, DISTANCE_METERS, FORCE_NEWTONS, LOAD }

data class ClusterSegmentPatch(
    val target: com.hydrafit.app.core.domain.routine.ClusterSegmentTarget,
    val fields: Set<ClusterSegmentField>
)

enum class ClusterSegmentField { REPS, LOAD }

/** Resolves original ordered targets first, then scalar+cluster fallback, without rewriting either. */
fun originalTargets(entry: OccurrenceEntry): List<GuidedWorkoutTargetOverride> {
    val ordered = entry.setTargets
    ordered?.requireValid()
    require(ordered == null || ordered.sets == entry.sets) {
        "Ordered targets must match entry set count"
    }
    entry.cluster?.requireValid()
    return (0 until entry.sets).map { index ->
        val target = ordered?.targets?.getOrNull(index) ?: SetTarget(
            reps = entry.reps,
            durationSeconds = entry.durationSeconds,
            distanceMeters = entry.distanceMeters,
            forceNewtons = entry.forceNewtons,
            loadKind = entry.loadKind,
            weightKg = entry.weightKg,
            targetRir = null
        )
        val cluster = entry.cluster
        if (cluster == null) {
            GuidedWorkoutTargetOverride(index, target)
        } else {
            GuidedWorkoutTargetOverride(
                index,
                target.copy(
                    reps = cluster.openingSegment.reps,
                    weightKg = cluster.openingSegment.weightKg
                ),
                cluster
            )
        }
    }
}

fun applyGuidedTargetPatch(
    entry: OccurrenceEntry,
    existing: GuidedWorkoutTargets?,
    completedSetCount: Int,
    reset: Boolean,
    patches: Map<Int, GuidedTargetPatch>
): GuidedWorkoutTargets? {
    require(completedSetCount in 0..entry.sets)
    val original = originalTargets(entry)
    val current = original.toMutableList().also { values ->
        existing?.also { it.requireValid(entry) }?.overrides?.forEach { values[it.setIndex] = it }
    }
    if (reset) {
        val retained = current.filter {
            it.setIndex < completedSetCount &&
                existing?.overrides?.any { old -> old.setIndex == it.setIndex } == true
        }.toMutableList()
        val resetBase = original.toMutableList()
        retained.forEach { resetBase[it.setIndex] = it }
        patches.forEach { (index, patch) ->
            require(index in completedSetCount until entry.sets) {
                "Only remaining sets may be edited"
            }
            resetBase[index] = mergePatch(entry, resetBase[index], patch)
        }
        return resetBase.filterIndexed { index, value -> value != original[index] }
            .takeIf { it.isNotEmpty() }
            ?.let { GuidedWorkoutTargets(overrides = it) }
            ?.also { it.requireValid(entry) }
    }
    patches.forEach { (index, patch) ->
        require(index in completedSetCount until entry.sets) { "Only remaining sets may be edited" }
        current[index] = mergePatch(entry, current[index], patch)
    }
    return current.filterIndexed { index, value -> value != original[index] }
        .takeIf { it.isNotEmpty() }?.let { GuidedWorkoutTargets(overrides = it) }
        ?.also { it.requireValid(entry) }
}

private fun mergePatch(
    entry: OccurrenceEntry,
    current: GuidedWorkoutTargetOverride,
    patch: GuidedTargetPatch
): GuidedWorkoutTargetOverride {
    val old = current.target
    val supplied = patch.target
    supplied.requireValid()
    val next = old.copy(
        reps = if (GuidedTargetField.REPS in patch.fields) supplied.reps else old.reps,
        durationSeconds = if (GuidedTargetField.DURATION_SECONDS in
            patch.fields
        ) {
            supplied.durationSeconds
        } else {
            old.durationSeconds
        },
        distanceMeters = if (GuidedTargetField.DISTANCE_METERS in
            patch.fields
        ) {
            supplied.distanceMeters
        } else {
            old.distanceMeters
        },
        forceNewtons = if (GuidedTargetField.FORCE_NEWTONS in
            patch.fields
        ) {
            supplied.forceNewtons
        } else {
            old.forceNewtons
        },
        loadKind = if (GuidedTargetField.LOAD in patch.fields) supplied.loadKind else old.loadKind,
        weightKg = if (GuidedTargetField.LOAD in patch.fields) supplied.weightKg else old.weightKg
    )
    require(GuidedTargetField.REPS !in patch.fields || old.reps != null)
    require(
        GuidedTargetField.DURATION_SECONDS !in patch.fields || old.durationSeconds != null
    )
    require(
        GuidedTargetField.DISTANCE_METERS !in patch.fields || old.distanceMeters != null
    )
    require(
        GuidedTargetField.FORCE_NEWTONS !in patch.fields || old.forceNewtons != null
    )
    WorkoutLoadPolicy.validate(entry.loadCapability, next.loadKind, next.weightKg)
    next.requireValid()
    var cluster = current.cluster
    if (cluster != null &&
        patch.fields.any { it == GuidedTargetField.REPS || it == GuidedTargetField.LOAD }
    ) {
        val opening = cluster.openingSegment.copy(
            reps = if (GuidedTargetField.REPS in
                patch.fields
            ) {
                requireNotNull(next.reps)
            } else {
                cluster.openingSegment.reps
            },
            weightKg = if (GuidedTargetField.LOAD in
                patch.fields
            ) {
                requireNotNull(next.weightKg) { "A cluster requires external load" }
            } else {
                cluster.openingSegment.weightKg
            }
        )
        cluster = cluster.copy(segments = listOf(opening) + cluster.segments.drop(1))
    }
    patch.laterClusterSegments.forEach { (segmentIndex, segmentPatch) ->
        val activeCluster = requireNotNull(cluster) { "Cluster segment edits require a cluster" }
        require(segmentIndex >= 1 && segmentIndex < activeCluster.segments.size)
        val prior = activeCluster.segments[segmentIndex]
        segmentPatch.target.requireValid()
        val edited = prior.copy(
            reps = if (ClusterSegmentField.REPS in
                segmentPatch.fields
            ) {
                segmentPatch.target.reps
            } else {
                prior.reps
            },
            weightKg = if (ClusterSegmentField.LOAD in
                segmentPatch.fields
            ) {
                segmentPatch.target.weightKg
            } else {
                prior.weightKg
            }
        )
        cluster = activeCluster.copy(
            segments = activeCluster.segments.toMutableList().also { it[segmentIndex] = edited }
        )
    }
    val result = GuidedWorkoutTargetOverride(current.setIndex, next, cluster)
    result.requireValid(entry.sets, entry.loadCapability)
    return result
}

private fun requireSameMetric(original: GuidedWorkoutTargetOverride, target: SetTarget) {
    require((original.target.reps != null) == (target.reps != null))
    require((original.target.durationSeconds != null) == (target.durationSeconds != null))
    require((original.target.distanceMeters != null) == (target.distanceMeters != null))
    require((original.target.forceNewtons != null) == (target.forceNewtons != null))
}

private fun requireLoadKindCompatible(capability: ExerciseLoadCapability, kind: LoadKind) {
    require(WorkoutLoadPolicy.permits(capability, kind)) {
        "Guided target load kind is incompatible with the frozen exercise"
    }
}
