package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
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

    private fun exercise(id: String, pattern: MovementPattern) = Exercise(
        id = id,
        name = id,
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = pattern
    )
}
