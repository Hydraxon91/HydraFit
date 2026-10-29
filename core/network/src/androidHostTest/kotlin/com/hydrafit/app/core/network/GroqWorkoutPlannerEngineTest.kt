package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerCandidateEligibility
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

class GroqWorkoutPlannerEngineTest {
    private val catalog = object : ExerciseCatalog {
        override suspend fun all() = listOf(
            exercise("press", MovementPattern.HORIZONTAL_PUSH),
            exercise("row", MovementPattern.HORIZONTAL_PULL),
            exercise("squat", MovementPattern.SQUAT),
            exercise("curl", MovementPattern.BICEPS_ISOLATION)
        )
    }
    private val fallback = object : WorkoutPlannerEngine {
        override val id = PlannerEngineId.DETERMINISTIC
        override suspend fun generatePlan(request: PlanRequest) = WeeklyPlan(id, emptyList())
    }
    private val request = PlanRequest(3, setOf(EquipmentTag.BARBELL), emptyMap(), nowMillis = 0L)

    @Test
    fun sendsGroqKeyAndMapsRateLimits() = runTest {
        var authorization: String? = null
        var requestBody = ""
        val mock = MockEngine { incoming ->
            authorization = incoming.headers[HttpHeaders.Authorization]
            requestBody = (incoming.body as TextContent).text
            respond(
                "{}",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createGeminiHttpClient(mock)
        val sanitizer = WeeklyPlanSanitizer(catalog)
        val builder = GeminiWorkoutPlannerEngine(
            client,
            GeminiConfig(),
            catalog,
            ApiKeyProvider { "unused" },
            sanitizer,
            fallback
        )
        val engine =
            GroqWorkoutPlannerEngine(
                client,
                catalog,
                GroqApiKeyProvider {
                    "test-key"
                },
                sanitizer,
                builder
            )
        val failure = assertFailsWith<PlanGenerationException> { engine.generatePlan(request) }
        assertEquals(PlanFailureReason.RATE_LIMITED, failure.reason)
        assertEquals("Bearer test-key", authorization)
        assertTrue(requestBody.contains("openai/gpt-oss-120b"))
        assertTrue(requestBody.contains("response_format"))
    }

    @Test
    fun parsesAndSanitizesSuccessfulGroqPlan() = runTest {
        val content = """{"days":[""" + listOf("press", "row", "squat").joinToString(",") {
            """{"focus":"FULL_BODY","exercises":[{"exerciseId":"$it","sets":3,"reps":8},""" +
                """{"exerciseId":"curl","sets":2,"reps":10}]}"""
        } + "]}"
        val response = geminiJson.encodeToString(
            GroqResponse(listOf(GroqChoice(GroqMessage("assistant", content))))
        )
        val mock = MockEngine {
            respond(
                response,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createGeminiHttpClient(mock)
        val sanitizer = WeeklyPlanSanitizer(catalog)
        val builder = GeminiWorkoutPlannerEngine(
            client,
            GeminiConfig(),
            catalog,
            ApiKeyProvider { "unused" },
            sanitizer,
            fallback
        )
        val engine = GroqWorkoutPlannerEngine(
            client,
            catalog,
            GroqApiKeyProvider { "test-key" },
            sanitizer,
            builder
        )
        val plan = engine.generatePlan(request)
        assertEquals(PlannerEngineId.GROQ_API, plan.engine)
        assertEquals(3, plan.days.size)
    }

    @Test
    fun resolvesUppercasedIdsToTheirEligibleId() = runTest {
        val content = """{"days":[""" + listOf("press", "row", "squat").joinToString(",") {
            """{"focus":"FULL_BODY","exercises":[""" +
                """{"exerciseId":"${it.uppercase()}","sets":3,"reps":8},""" +
                """{"exerciseId":"curl","sets":2,"reps":10}]}"""
        } + "]}"
        val parsed = parseWeeklyPlan(content, PlannerEngineId.GROQ_API)
        val candidates = catalog.all()
        val resolved = resolveExerciseIds(parsed, candidates)
        assertEquals(
            listOf("press", "row", "squat"),
            resolved.days.map { day -> day.exercises.first().exerciseId }
        )
    }

    @Test
    fun resolvesAnExactDisplayNameToItsEligibleId() = runTest {
        val named = exercise("press", MovementPattern.HORIZONTAL_PUSH, name = "Barbell Bench Press")
        val content = """{"days":[{"focus":"FULL_BODY","exercises":[""" +
            """{"exerciseId":"Barbell Bench Press","sets":3,"reps":8}]}]}"""
        val parsed = parseWeeklyPlan(content, PlannerEngineId.GROQ_API)

        val resolved = resolveExerciseIds(parsed, listOf(named))

        assertEquals("press", resolved.days.single().exercises.single().exerciseId)
    }

    @Test
    fun leavesASharedDisplayNameUnresolvedInsteadOfChoosingARow() = runTest {
        val shared = listOf(
            exercise("press-a", MovementPattern.HORIZONTAL_PUSH, name = "Bench Press"),
            exercise("press-b", MovementPattern.HORIZONTAL_PUSH, name = "Bench Press")
        )
        val content = """{"days":[{"focus":"FULL_BODY","exercises":[""" +
            """{"exerciseId":"bench press","sets":3,"reps":8}]}]}"""

        listOf(shared, shared.reversed()).forEach { candidates ->
            val resolved = resolveExerciseIds(
                parseWeeklyPlan(content, PlannerEngineId.GROQ_API),
                candidates
            )

            // The name cannot say which exercise was meant, so the sanitizer drops the slot
            // instead of the app silently choosing one of the two.
            assertEquals(
                "bench press",
                resolved.days.single().exercises.single().exerciseId
            )
        }
    }

    @Test
    fun resolvesASharedNameToTheOnlyEligibleMatch() = runTest {
        val shared = listOf(
            exercise("bench-barbell", MovementPattern.HORIZONTAL_PUSH, name = "Bench Press"),
            exercise(
                "bench-cable",
                MovementPattern.HORIZONTAL_PUSH,
                name = "Bench Press",
                equipment = setOf(EquipmentTag.CABLE_MACHINE)
            )
        )
        val candidates = PlannerCandidateEligibility.candidates(shared, request)
        assertEquals(listOf("bench-barbell"), candidates.map { it.id })
        val content = """{"days":[{"focus":"FULL_BODY","exercises":[""" +
            """{"exerciseId":"Bench Press","sets":3,"reps":8}]}]}"""

        val resolved = resolveExerciseIds(
            parseWeeklyPlan(content, PlannerEngineId.GROQ_API),
            candidates
        )

        assertEquals("bench-barbell", resolved.days.single().exercises.single().exerciseId)
    }

    @Test
    fun keepsTheServiceUnavailableReasonForALongRetryHintOnA5xx() = runTest {
        var calls = 0
        val mock = MockEngine {
            calls++
            respond(
                """{"error":{"message":"overloaded"}}""",
                HttpStatusCode.ServiceUnavailable,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("120")
                )
            )
        }
        val failure = assertFailsWith<PlanGenerationException> {
            engineFor(mock).generatePlan(request)
        }
        assertEquals(PlanFailureReason.SERVICE_UNAVAILABLE, failure.reason)
        assertEquals(1, calls)
    }

    @Test
    fun keepsTheRateLimitReasonForALongRetryHint() = runTest {
        val mock = MockEngine {
            respond(
                """{"error":{"code":"rate_limit_exceeded","message":"tokens per minute"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("120")
                )
            )
        }
        val failure = assertFailsWith<PlanGenerationException> {
            engineFor(mock).generatePlan(request)
        }
        assertEquals(PlanFailureReason.RATE_LIMITED, failure.reason)
    }

    @Test
    fun mapsADailyQuotaWithoutRetrying() = runTest {
        var calls = 0
        val mock = MockEngine {
            calls++
            respond(
                """{"error":{"code":"rate_limit_exceeded",""" +
                    """"message":"Rate limit reached for tokens per day (TPD)"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val failure = assertFailsWith<PlanGenerationException> {
            engineFor(mock).generatePlan(request)
        }
        assertEquals(PlanFailureReason.QUOTA_EXHAUSTED, failure.reason)
        assertEquals(1, calls)
    }

    @Test
    fun retriesATransientRateLimitThenReportsIt() = runTest {
        var calls = 0
        val mock = MockEngine {
            calls++
            respond(
                """{"error":{"code":"rate_limit_exceeded","message":"tokens per minute"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val failure = assertFailsWith<PlanGenerationException> {
            engineFor(mock).generatePlan(request)
        }
        assertEquals(PlanFailureReason.RATE_LIMITED, failure.reason)
        assertEquals(3, calls)
    }

    private fun engineFor(mock: MockEngine): GroqWorkoutPlannerEngine {
        val client = createGeminiHttpClient(mock)
        val sanitizer = WeeklyPlanSanitizer(catalog)
        val builder = GeminiWorkoutPlannerEngine(
            client,
            GeminiConfig(),
            catalog,
            ApiKeyProvider { "unused" },
            sanitizer,
            fallback
        )
        return GroqWorkoutPlannerEngine(
            client,
            catalog,
            GroqApiKeyProvider { "test-key" },
            sanitizer,
            builder
        )
    }

    private suspend fun generateWithSingleReply(content: String): WeeklyPlan {
        val response = geminiJson.encodeToString(
            GroqResponse(listOf(GroqChoice(GroqMessage("assistant", content))))
        )
        val mock = MockEngine {
            respond(
                response,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return engineFor(mock).generatePlan(request)
    }

    @Test
    fun mapsProviderJsonValidationFailureToInvalidResponse() = runTest {
        val mock = MockEngine {
            respond(
                """{"error":{"code":"json_validate_failed"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = createGeminiHttpClient(mock)
        val sanitizer = WeeklyPlanSanitizer(catalog)
        val builder = GeminiWorkoutPlannerEngine(
            client,
            GeminiConfig(),
            catalog,
            ApiKeyProvider { "unused" },
            sanitizer,
            fallback
        )
        val engine = GroqWorkoutPlannerEngine(
            client,
            catalog,
            GroqApiKeyProvider { "test-key" },
            sanitizer,
            builder
        )
        val failure = assertFailsWith<PlanGenerationException> { engine.generatePlan(request) }
        assertEquals(PlanFailureReason.INVALID_RESPONSE, failure.reason)
    }

    private fun exercise(
        id: String,
        pattern: MovementPattern,
        name: String = id,
        equipment: Set<EquipmentTag> = setOf(EquipmentTag.BARBELL)
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = pattern
    )
}
