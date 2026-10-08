package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanFailureReason
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalCoroutinesApi::class)
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

        engine(mockEngine).generatePlan(
            request(setsPerExercise = 5, accessorySetsPerExercise = 2)
        )

        val requestData = requireNotNull(captured)
        assertEquals("test-key", requestData.headers["x-goog-api-key"])
        val bodyText = (requestData.body as TextContent).text
        assertTrue(bodyText.contains("responseSchema"), "structured output schema should be sent")
        assertTrue(bodyText.contains("bench-press"), "catalog ids should be offered to the model")
        assertTrue(bodyText.contains("Day 1: "), "per-day focus schedule should be sent")
        assertTrue(
            bodyText.contains("5 sets for compound lifts"),
            "compound set count should be sent"
        )
        assertTrue(
            bodyText.contains("2 sets for accessory exercises"),
            "accessory set count should be sent"
        )
    }

    @Test
    fun pinsTheModelIdInTheGeneratedUrl() = runTest {
        assertEquals("gemini-3.1-flash-lite", GeminiConfig().model)

        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request())

        val url = requireNotNull(captured).url.toString()
        assertTrue(url.contains("/models/gemini-3.1-flash-lite:generateContent"), url)
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
        assertTrue(bodyText.contains("15 reps for compound exercises"), bodyText)
        assertTrue(bodyText.contains("15 reps for accessory exercises"), bodyText)
        assertTrue(bodyText.contains("about 4 sets per muscle across the week"), bodyText)
    }

    @Test
    fun mentionsTheDeloadWeekInThePrompt() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(request(isDeload = true))

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("deload week"), bodyText)
    }

    @Test
    fun sendsEquipmentWeightCapsAndPeriodization() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(
            request(
                equipmentMaxWeights = mapOf(EquipmentTag.BARBELL to 200.0),
                weekNumber = 4,
                cycleNumber = 2
            )
        )

        val bodyText = (requireNotNull(captured).body as TextContent).text
        assertTrue(bodyText.contains("Equipment weight limits (do not exceed)"), bodyText)
        assertTrue(bodyText.contains("Barbell: 200.0kg"), bodyText)
        assertTrue(bodyText.contains("Periodization: week 4 of cycle 2."), bodyText)
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

        val failure = assertFailsWith<PlanGenerationException> { engine.generatePlan(request()) }

        assertEquals(PlanFailureReason.INVALID_API_KEY, failure.reason)
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
    fun mapsRateLimitToRateLimitedAndRetries() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(
                """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"rate"}}""",
                HttpStatusCode.TooManyRequests,
                jsonHeaders()
            )
        }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(PlanFailureReason.RATE_LIMITED, failure.reason)
        assertEquals(3, calls)
    }

    @Test
    fun honorsRetryAfterHintUpToTheCeiling() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"rate"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                        HttpHeaders.RetryAfter to listOf("30")
                    )
                )
            } else {
                respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
            }
        }

        engine(mockEngine).generatePlan(request())

        assertEquals(2, calls)
        assertEquals(30_000L, testScheduler.currentTime)
    }

    @Test
    fun honorsRetryInfoBodyHintUpToTheCeiling() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            if (calls == 1) {
                respond(
                    """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"rate",""" +
                        """"details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo",""" +
                        """"retryDelay":"30s"}]}}""",
                    HttpStatusCode.TooManyRequests,
                    jsonHeaders()
                )
            } else {
                respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
            }
        }

        engine(mockEngine).generatePlan(request())

        assertEquals(2, calls)
        assertEquals(30_000L, testScheduler.currentTime)
    }

    @Test
    fun rejectsRetryHintAboveTheCeilingAsRateLimited() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(
                """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"rate"}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.RetryAfter to listOf("120")
                )
            )
        }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(PlanFailureReason.RATE_LIMITED, failure.reason)
        assertTrue(failure.transient)
        assertEquals(1, calls)
    }

    @Test
    fun mapsDailyQuotaToQuotaExhaustedWithoutRetrying() = runTest {
        var calls = 0
        val mockEngine = MockEngine {
            calls++
            respond(
                """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED",""" +
                    """"message":"Quota exceeded, limit per day reached"}}""",
                HttpStatusCode.TooManyRequests,
                jsonHeaders()
            )
        }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(PlanFailureReason.QUOTA_EXHAUSTED, failure.reason)
        assertFalse(failure.transient)
        assertEquals(1, calls)
    }

    @Test
    fun loadsTheCatalogOncePerGeneration() = runTest {
        var catalogCalls = 0
        val countingCatalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> {
                catalogCalls++
                return FakeCatalog.all()
            }
        }
        val engine = GeminiWorkoutPlannerEngine(
            httpClient = createGeminiHttpClient(respondEnvelope(VALID_PLAN)),
            config = GeminiConfig(),
            catalog = countingCatalog,
            apiKeyProvider = ApiKeyProvider { "test-key" },
            sanitizer = WeeklyPlanSanitizer(FakeCatalog),
            fallback = FallbackEngine
        )

        engine.generatePlan(request())

        assertEquals(1, catalogCalls)
    }

    @Test
    fun mapsAuthFailureToInvalidApiKey() = runTest {
        val mockEngine =
            MockEngine { respond(geminiError(), HttpStatusCode.Forbidden, jsonHeaders()) }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(PlanFailureReason.INVALID_API_KEY, failure.reason)
    }

    @Test
    fun mapsTransportFailureToNetworkReason() = runTest {
        val mockEngine = MockEngine { throw java.io.IOException("no route to host") }

        val failure = assertFailsWith<PlanGenerationException> {
            engine(mockEngine).generatePlan(request())
        }

        assertEquals(PlanFailureReason.NETWORK, failure.reason)
        assertTrue(failure.transient)
    }

    @Test
    fun failsWhenResponseHasNoCandidate() = runTest {
        val engine = engine(respondRaw("""{"candidates":[]}"""))

        val failure = assertFailsWith<PlanGenerationException> { engine.generatePlan(request()) }

        assertEquals(PlanFailureReason.INVALID_RESPONSE, failure.reason)
    }

    @Test
    fun fallsBackWhenThePlanHasTooFewDays() = runTest {
        val shortPlan = planJson(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl")
            )
        )

        val plan = engine(respondEnvelope(shortPlan)).generatePlan(request(daysPerWeek = 3))

        assertEquals(PlannerEngineId.DETERMINISTIC, plan.engine)
    }

    @Test
    fun dropsExercisesThatNeedUnavailableEquipmentAndFallsBack() = runTest {
        val plan = planJson(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl"),
                "LEGS" to listOf("bench-press", "dumbbell-curl")
            )
        )

        val result = engine(respondEnvelope(plan)).generatePlan(request(daysPerWeek = 3))

        assertEquals(PlannerEngineId.DETERMINISTIC, result.engine)
    }

    @Test
    fun appliesRequestedSetsAndVolumeAwareReps() = runTest {
        val plan = engine(respondEnvelope(VALID_PLAN)).generatePlan(request(setsPerExercise = 5))

        val planned = plan.days.first().exercises.first { it.exerciseId == "bench-press" }
        assertEquals(5, planned.sets)
        // The set override carries volume; reps stay at the goal's compound band.
        assertEquals(6, planned.reps)
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

    @Test
    fun sendsProgressedWeightsOnlyWhenSharingIsEnabled() = runTest {
        var captured: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            captured = request
            respond(envelope(VALID_PLAN), HttpStatusCode.OK, jsonHeaders())
        }

        engine(mockEngine).generatePlan(
            request(suggestedWeightsKg = mapOf("bench-press" to 90.0))
        )
        val off = (requireNotNull(captured).body as TextContent).text
        assertFalse(off.contains("Progressed starting weights"), off)

        engine(mockEngine).generatePlan(
            request(
                includeWorkoutData = true,
                suggestedWeightsKg = mapOf("bench-press" to 90.0)
            )
        )
        val on = (requireNotNull(captured).body as TextContent).text
        assertTrue(on.contains("Progressed starting weights"), on)
        assertTrue(on.contains("bench-press: 90.0kg"), on)
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
        accessorySetsPerExercise: Int = goal.accessorySets,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
        suggestedWeightsKg: Map<String, Double> = emptyMap(),
        equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
        weekNumber: Int = 1,
        cycleNumber: Int = 1,
        includeWorkoutData: Boolean = false,
        isDeload: Boolean = false,
        recentWeights: List<WeightHistoryEntry> = emptyList()
    ) = PlanRequest(
        daysPerWeek = daysPerWeek,
        availableEquipment = setOf(EquipmentTag.BARBELL),
        muscleFatigue = emptyMap(),
        nowMillis = 0L,
        goal = goal,
        setsPerExercise = setsPerExercise,
        accessorySetsPerExercise = accessorySetsPerExercise,
        recentExerciseIdsByPattern = recentExerciseIdsByPattern,
        suggestedWeightsKg = suggestedWeightsKg,
        equipmentMaxWeights = equipmentMaxWeights,
        weekNumber = weekNumber,
        cycleNumber = cycleNumber,
        includeWorkoutData = includeWorkoutData,
        isDeload = isDeload,
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
            exercise("barbell-row", MovementPattern.HORIZONTAL_PULL, EquipmentTag.BARBELL),
            exercise("barbell-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.BARBELL),
            exercise("dumbbell-curl", MovementPattern.BICEPS_ISOLATION, EquipmentTag.DUMBBELL)
        )

        private fun exercise(id: String, pattern: MovementPattern, equipment: EquipmentTag) =
            Exercise(
                id = id,
                name = id,
                requiredEquipment = setOf(equipment),
                primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
                movementPattern = pattern
            )
    }

    private companion object {
        // Distinct foci, and each day pairs a distinct compound with the same (reusable) accessory.
        val DAY = listOf("bench-press", "barbell-curl")
        val VALID_PLAN = planJson(
            listOf(
                "PUSH" to listOf("bench-press", "barbell-curl"),
                "PULL" to listOf("barbell-row", "barbell-curl"),
                "LEGS" to listOf("overhead-press", "barbell-curl")
            )
        )

        fun planJson(days: List<Pair<String, List<String>>>): String = days.joinToString(
            prefix = """{"days":[""",
            postfix = "]}",
            separator = ","
        ) { (focus, exercises) ->
            val items = exercises.joinToString(",") {
                """{"exerciseId":"$it","sets":3,"reps":8}"""
            }
            """{"focus":"$focus","exercises":[$items]}"""
        }
    }
}
