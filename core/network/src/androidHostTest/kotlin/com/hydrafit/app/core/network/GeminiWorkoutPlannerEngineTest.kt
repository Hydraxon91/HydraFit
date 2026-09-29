package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WeightHistoryEntry
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

class GeminiWorkoutPlannerEngineTest {

    @Test
    fun mapsStructuredResponseToWeeklyPlan() = runTest {
        val plan = engine(respondEnvelope(VALID_PLAN)).generatePlan(request())

        assertEquals(PlannerEngineId.GEMINI_API, plan.engine)
        assertEquals(3, plan.days.size)
        assertEquals(SplitFocus.PUSH, plan.days.first().focus)
        assertEquals("bench-press", plan.days.first().exercises.first().exerciseId)
    }

    @Test
    fun sendsApiKeyHeaderStructuredSchemaAndPlanningInputs() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request(setsPerExercise = 5))

        val requestData = requireNotNull(captured)
        assertEquals("test-key", requestData.headers["x-goog-api-key"])
        val bodyText = (requestData.body as TextContent).text
        assertTrue(bodyText.contains("responseSchema"), "structured output schema should be sent")
        assertTrue(bodyText.contains("bench-press"), "catalog ids should be offered to the model")
        assertTrue(bodyText.contains("Split preference: AUTO"), "split preference should be sent")
        assertTrue(bodyText.contains("Use exactly 5 sets"), "set count should be sent")
    }

    @Test
    fun sendsGoalSpecificRepTargets() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request(goal = TrainingGoal.ENDURANCE))

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("Training goal: ENDURANCE"), bodyText)
        assertTrue(bodyText.contains("Use 15 reps for compound lifts"), bodyText)
        assertTrue(bodyText.contains("15 reps for isolation exercises"), bodyText)
    }

    @Test
    fun sendsRecentAcceptedSelectionsToSteerRotation() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(
            request(
                recentExerciseIdsByPattern = mapOf(
                    MovementPattern.HORIZONTAL_PUSH to setOf("bench-press")
                )
            )
        )

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("previous accepted week"), bodyText)
        assertTrue(bodyText.contains("HORIZONTAL_PUSH: bench-press"), bodyText)
    }

    @Test
    fun boundsArrayLengthsInTheSchema() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request(daysPerWeek = 3))

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("\"minItems\":3"), "days should be bounded to 3")
        assertTrue(bodyText.contains("\"maxItems\":3"), "days should be bounded to 3")
        assertTrue(bodyText.contains("\"minItems\":4"), "exercises per day should have a floor")
        assertTrue(bodyText.contains("\"maxItems\":6"), "exercises per day should have a ceiling")
    }

    @Test
    fun readsApiKeyWhenSendingTheRequest() = runTest {
        var apiKey = ""
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }
        val planner = engine(mockEngine, apiKeyProvider = ApiKeyProvider { apiKey })

        apiKey = "saved-after-engine-construction"
        planner.generatePlan(request())

        assertEquals(
            "saved-after-engine-construction",
            requireNotNull(captured).headers["x-goog-api-key"]
        )
    }

    @Test
    fun rejectsMissingApiKey() = runTest {
        val engine = engine(respondEnvelope(VALID_PLAN), apiKey = "")

        assertFailsWith<IllegalArgumentException> { engine.generatePlan(request()) }
    }

    @Test
    fun retriesTransientFailuresThenSucceeds() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            if (calls < 3) {
                respond(geminiError(), HttpStatusCode.ServiceUnavailable, jsonHeaders())
            } else {
                respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
            }
        }

        val plan = engine(mockEngine).generatePlan(request())

        assertEquals(3, calls)
        assertEquals(PlannerEngineId.GEMINI_API, plan.engine)
    }

    @Test
    fun givesUpAfterMaxRetriesWithATransientFailure() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(geminiError(), HttpStatusCode.ServiceUnavailable, jsonHeaders())
        }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(3, calls)
        assertTrue(failure.transient)
    }

    @Test
    fun doesNotRetryClientErrors() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(geminiError(), HttpStatusCode.BadRequest, jsonHeaders())
        }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(1, calls)
        assertFalse(failure.transient)
        assertTrue(
            failure.message!!.contains("high demand"),
            "the backend error message should be surfaced"
        )
    }

    @Test
    fun failsWhenResponseHasNoCandidate() = runTest {
        val engine = engine(respondRaw("""{"candidates":[]}"""))

        assertFailsWith<IllegalStateException> { engine.generatePlan(request()) }
    }

    @Test
    fun fallsBackWhenThePlanHasTooFewDays() = runTest {
        val shortPlan = planJson(List(2) { DAY })

        val plan = engine(respondEnvelope(shortPlan)).generatePlan(request(daysPerWeek = 3))

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun dropsExercisesThatNeedUnavailableEquipmentAndFallsBack() = runTest {
        val plan = planJson(
            listOf(
                DAY,
                DAY,
                listOf("bench-press", "dumbbell-curl")
            )
        )

        val result = engine(respondEnvelope(plan)).generatePlan(request(daysPerWeek = 3))

        assertEquals(PlannerEngineId.DETERMINISTIC, result.engine)
    }

    @Test
    fun appliesRequestedSetsAndCompoundIsolationReps() = runTest {
        val plan = engine(respondEnvelope(VALID_PLAN)).generatePlan(request(setsPerExercise = 5))

        val planned = plan.days.first().exercises.first { it.exerciseId == "bench-press" }
        assertEquals(5, planned.sets)
        assertEquals(DeterministicWorkoutPlannerEngine.COMPOUND_REPS, planned.reps)
    }

    @Test
    fun omitsWorkoutDataUnlessSharingIsEnabled() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request())

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertFalse(bodyText.contains("Recent working weights"), bodyText)
        assertFalse(bodyText.contains("suggestedWeightKg"), bodyText)
    }

    @Test
    fun sendsRecentWeightsWhenSharingIsEnabled() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(
            request(
                includeWorkoutData = true,
                recentWeights = listOf(WeightHistoryEntry("bench-press", 0L, 100.0, 5))
            )
        )

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("Recent working weights"), bodyText)
        assertTrue(bodyText.contains("bench-press"), bodyText)
        assertTrue(bodyText.contains("suggestedWeightKg"), bodyText)
    }

    private fun engine(
        engine: HttpClientEngine,
        apiKey: String = "test-key",
        apiKeyProvider: ApiKeyProvider = ApiKeyProvider { apiKey }
    ) = GeminiWorkoutPlannerEngine(
        httpClient = createGeminiHttpClient(engine),
        config = GeminiConfig(),
        catalog = FakeCatalog,
        apiKeyProvider = apiKeyProvider,
        sanitizer = WeeklyPlanSanitizer(FakeCatalog),
        fallback = FallbackEngine
    )

    private fun respondEnvelope(plan: String) = respondRaw(envelope(plan))

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

    private fun geminiError(): String =
        """{"error":{"code":503,"message":"high demand","status":"UNAVAILABLE",""" +
            """"details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"1s"}]}}"""

    private fun request(
        daysPerWeek: Int = 3,
        goal: TrainingGoal = TrainingGoal.BALANCED,
        setsPerExercise: Int = goal.defaultSets,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
        includeWorkoutData: Boolean = false,
        recentWeights: List<WeightHistoryEntry> = emptyList()
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        recentExerciseIdsByPattern = recentExerciseIdsByPattern,
        includeWorkoutData = includeWorkoutData,
        recentWeights = recentWeights
    )

    private object FallbackEngine : WorkoutPlannerEngine {
        override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

        override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
            WeeklyPlan(engine = PlannerEngineId.DETERMINISTIC, days = emptyList())
    }

    private object FakeCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            exercise("bench-press", MovementPattern.HORIZONTAL_PUSH, EquipmentTag.BARBELL),
            exercise("overhead-press", MovementPattern.VERTICAL_PUSH, EquipmentTag.BARBELL),
            exercise("dumbbell-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL)
        )

        private fun exercise(id: String, pattern: MovementPattern, equipment: EquipmentTag) =
            Exercise(
                id = id,
                name = id,
                requiredEquipment = setOf(equipment),
                primaryMuscles = setOf(MuscleGroup.CHEST),
                movementPattern = pattern
            )
    }

    private companion object {
        val DAY = listOf("bench-press", "overhead-press")
        val VALID_PLAN = planJson(List(3) { DAY })

        fun planJson(days: List<List<String>>): String = days.joinToString(
            prefix = """{"days":[""",
            postfix = "]}",
            separator = ","
        ) { exercises ->
            val items = exercises.joinToString(",") {
                """{"exerciseId":"$it","sets":3,"reps":8}"""
            }
            """{"focus":"PUSH","exercises":[$items]}"""
        }
    }
}
