package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
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

class GeminiWorkoutPlannerEngineTest {

    @Test
    fun mapsStructuredResponseToWeeklyPlan() = runTest {
        val plan = engine(respondEnvelope()).generatePlan(request())

        assertEquals(PlannerEngineId.GEMINI_API, plan.engine)
        assertEquals(1, plan.days.size)
        assertEquals(SplitFocus.PUSH, plan.days.single().focus)
        val planned = plan.days.single().exercises.single()
        assertEquals("bench-press", planned.exerciseId)
        assertEquals(3, planned.sets)
        assertEquals(8, planned.reps)
    }

    @Test
    fun sendsApiKeyHeaderAndStructuredOutputSchema() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(PLAN_JSON), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request())

        val requestData = requireNotNull(captured)
        assertEquals("test-key", requestData.headers["x-goog-api-key"])
        val bodyText = (requestData.body as TextContent).text
        assertTrue(bodyText.contains("responseSchema"), "structured output schema should be sent")
        assertTrue(bodyText.contains("bench-press"), "catalog ids should be offered to the model")
    }

    @Test
    fun rejectsMissingApiKey() = runTest {
        val engine = engine(respondEnvelope(), apiKey = "")

        assertFailsWith<IllegalArgumentException> { engine.generatePlan(request()) }
    }

    @Test
    fun failsOnNonSuccessStatus() = runTest {
        val mockEngine = MockEngine { respond("boom", HttpStatusCode.InternalServerError) }

        assertFailsWith<IllegalStateException> { engine(mockEngine).generatePlan(request()) }
    }

    @Test
    fun failsWhenResponseHasNoCandidate() = runTest {
        val engine = engine(respondRaw("""{"candidates":[]}"""))

        assertFailsWith<IllegalStateException> { engine.generatePlan(request()) }
    }

    private fun engine(engine: HttpClientEngine, apiKey: String = "test-key") =
        GeminiWorkoutPlannerEngine(
            httpClient = createGeminiHttpClient(engine),
            config = GeminiConfig(apiKey = apiKey),
            catalog = FakeCatalog
        )

    private fun respondEnvelope() = respondRaw(envelope(PLAN_JSON))

    private fun respondRaw(body: String) = MockEngine {
        respond(body, HttpStatusCode.OK, jsonHeaders())
    }

    private fun envelope(planJson: String): String = geminiJson.encodeToString(
        GeminiResponse(
            candidates = listOf(
                GeminiCandidate(content = GeminiContent(parts = listOf(GeminiPart(planJson))))
            )
        )
    )

    private fun jsonHeaders() =
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun request() = PlanRequest(
        daysPerWeek = 3,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L
    )

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST)
            )
        )
    }

    private companion object {
        const val PLAN_JSON =
            """{"days":[{"focus":"PUSH","exercises":[{"exerciseId":"bench-press","sets":3,"reps":8}]}]}"""
    }
}
