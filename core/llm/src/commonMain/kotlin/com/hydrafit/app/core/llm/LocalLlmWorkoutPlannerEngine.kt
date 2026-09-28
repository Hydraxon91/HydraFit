package com.hydrafit.app.core.llm

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannedExercise
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalLlmWorkoutPlannerEngine(
    private val generator: OnDeviceTextGenerator,
    private val fallback: WorkoutPlannerEngine,
    private val catalog: ExerciseCatalog
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.LOCAL_LLM

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        if (!generator.isAvailable()) return fallback.generatePlan(request)

        var attempt = 0
        while (attempt < MAX_ATTEMPTS) {
            attempt++
            val plan = try {
                // Loading the model and generating are blocking, so keep them off the main thread.
                val output = withContext(Dispatchers.Default) {
                    generator.generate(prompt(request))
                }
                sanitizedPlan(parseWeeklyPlan(output, PlannerEngineId.LOCAL_LLM), request)
            } catch (outOfMemory: OutOfMemoryError) {
                return fallback.generatePlan(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (plan != null) return plan
        }
        return fallback.generatePlan(request)
    }

    /**
     * Small models often return a shorter or partially invented plan with copied
     * sets/reps, so drop unknown exercises, apply the requested sets and the
     * compound/isolation rep scheme, and reject a plan that is not a complete week.
     */
    private suspend fun sanitizedPlan(plan: WeeklyPlan, request: PlanRequest): WeeklyPlan? {
        val catalogById = catalog.all().associateBy { it.id }
        val days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.mapNotNull { planned ->
                    val exercise = catalogById[planned.exerciseId] ?: return@mapNotNull null
                    PlannedExercise(
                        exerciseId = exercise.id,
                        sets = request.setsPerExercise,
                        reps = if (exercise.movementPattern.isCompound) {
                            DeterministicWorkoutPlannerEngine.COMPOUND_REPS
                        } else {
                            DeterministicWorkoutPlannerEngine.ISOLATION_REPS
                        }
                    )
                }
            )
        }
        if (days.size < request.daysPerWeek) return null
        if (days.any { it.exercises.size < MIN_EXERCISES_PER_DAY }) return null
        return plan.copy(
            days = days.take(request.daysPerWeek)
                .mapIndexed { index, day -> day.copy(dayIndex = index) }
        )
    }

    private suspend fun prompt(request: PlanRequest): String {
        val equipment = request.availableEquipment.joinToString(", ") { it.name }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val exerciseIds = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .joinToString(", ") { it.id }

        val sets = request.setsPerExercise
        return buildString {
            appendLine("You are a strength coach. Reply with JSON only, no markdown, no prose.")
            appendLine("Build a plan with exactly ${request.daysPerWeek} days.")
            appendLine("Available equipment: $equipment")
            appendLine("Muscle fatigue (0.0-1.0): $fatigue")
            appendLine("Give every day 4 to 6 different exercises from this list: $exerciseIds")
            appendLine(
                "Use one focus value per day from: PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY"
            )
            appendLine("Use exactly $sets sets for every exercise.")
            appendLine("Use 6 reps for compound lifts and 12 reps for isolation exercises.")
            appendLine(
                "Example: " +
                    """{"days":[{"focus":"PUSH","exercises":""" +
                    """[{"exerciseId":"barbell-bench-press","sets":$sets,"reps":6},""" +
                    """{"exerciseId":"overhead-press","sets":$sets,"reps":6}]},""" +
                    """{"focus":"PULL","exercises":""" +
                    """[{"exerciseId":"barbell-row","sets":$sets,"reps":6},""" +
                    """{"exerciseId":"barbell-curl","sets":$sets,"reps":12}]}]}"""
            )
        }
    }

    private companion object {
        const val MIN_EXERCISES_PER_DAY = 2
        const val MAX_ATTEMPTS = 2
    }
}
