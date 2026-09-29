package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitResolver
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import com.hydrafit.app.core.domain.time.isoDateUtc
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GeminiWorkoutPlannerEngine(
    private val httpClient: HttpClient,
    private val config: GeminiConfig,
    private val catalog: ExerciseCatalog,
    private val apiKeyProvider: ApiKeyProvider,
    private val sanitizer: WeeklyPlanSanitizer,
    private val fallback: WorkoutPlannerEngine
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.GEMINI_API

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        val apiKey = apiKeyProvider.geminiApiKey()
        require(apiKey.isNotBlank()) { "Gemini API key is not configured" }
        val payload = buildRequest(request)
        val url = "${config.baseUrl}/models/${config.model}:generateContent"

        var attempt = 0
        while (true) {
            val response = httpClient.post(url) {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (response.status.isSuccess()) {
                val text = response.body<GeminiResponse>()
                    .candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: error("Gemini response contained no content")
                val plan = parseWeeklyPlan(text, PlannerEngineId.GEMINI_API)
                return sanitizer.sanitize(plan, request) ?: fallback.generatePlan(request)
            }

            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val transient = response.status.isTransient()
            if (!transient || attempt >= MAX_RETRIES) {
                throw PlanGenerationException(
                    transient = transient,
                    message = failureMessage(response.status, body)
                )
            }

            delay(retryDelayMillis(response, body, attempt))
            attempt++
        }
    }

    private fun failureMessage(status: HttpStatusCode, body: String): String {
        val detail = backendErrorMessage(body)
        val base = "Gemini request failed with status ${status.value}"
        return if (detail.isNullOrBlank()) base else "$base: $detail"
    }

    private fun backendErrorMessage(body: String): String? = runCatching {
        geminiJson.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

    private fun retryDelayMillis(response: HttpResponse, body: String, attempt: Int): Long {
        val hint = response.headers[HttpHeaders.RetryAfter]?.toRetryDelayMillis()
            ?: retryDelayFromBody(body)
            ?: backoffMillis(attempt)
        return hint.coerceIn(0L, MAX_BACKOFF_MILLIS)
    }

    private fun backoffMillis(attempt: Int): Long {
        val exponential = (BASE_BACKOFF_MILLIS shl attempt).coerceAtMost(MAX_BACKOFF_MILLIS)
        val half = exponential / 2
        return half + Random.nextLong(half + 1)
    }

    private fun retryDelayFromBody(body: String): Long? = runCatching {
        val details = geminiJson.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("details")
            ?.jsonArray
            ?: return@runCatching null
        details.firstNotNullOfOrNull { element ->
            val detail = element.jsonObject
            val type = detail["@type"]?.jsonPrimitive?.contentOrNull
            if (type?.endsWith("RetryInfo") == true) {
                detail["retryDelay"]?.jsonPrimitive?.contentOrNull?.toRetryDelayMillis()
            } else {
                null
            }
        }
    }.getOrNull()

    private fun String.toRetryDelayMillis(): Long? =
        removeSuffix("s").toDoubleOrNull()?.let { seconds -> (seconds * 1000).toLong() }

    private fun HttpStatusCode.isTransient(): Boolean = value in TRANSIENT_STATUS_CODES

    private suspend fun buildRequest(request: PlanRequest): GeminiRequest {
        val equipment = request.availableEquipment.joinToString(", ") { it.displayName }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val exerciseIds = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .joinToString(", ") { it.id }
        val focusSequence = SplitResolver.focusSequence(
            request.splitPreference,
            request.daysPerWeek
        )

        val prompt = buildString {
            appendLine("You are a strength coach. Build a weekly workout plan.")
            appendLine("Days per week: ${request.daysPerWeek}")
            appendLine("Use these focuses, one per day, in this exact order:")
            focusSequence.forEachIndexed { index, focus ->
                appendLine("Day ${index + 1}: ${focus.name}")
            }
            appendLine("Training goal: ${request.goal.name}")
            appendLine("Available equipment: $equipment")
            appendLine("Current muscle fatigue (0.0-1.0): $fatigue")
            if (request.isDeload) {
                appendLine(
                    "This is a deload week: use fewer sets and roughly 80% of the normal working " +
                        "weight to allow recovery."
                )
            }
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine("Give every day 4 to 6 exercises.")
            appendLine(
                "Give each day a different focus from the schedule above, and do not reuse a " +
                    "compound lift across days; isolation exercises may repeat."
            )
            appendLine(
                "Use ${request.setsPerExercise} sets for compound lifts and " +
                    "${request.accessorySetsPerExercise} sets for accessory exercises."
            )
            val compoundVolume = request.goal.defaultSets * request.goal.compoundReps
            val accessoryVolume = request.goal.accessorySets * request.goal.isolationReps
            appendLine(
                "Scale reps to keep volume steady: fewer sets mean more reps per set. Aim for " +
                    "about $compoundVolume total reps for compound lifts and " +
                    "$accessoryVolume for accessory exercises."
            )
            appendLine("Prefer exercises whose muscles are less fatigued.")
            val recentlyUsed = request.recentExerciseIdsByPattern
            if (recentlyUsed.isNotEmpty()) {
                val history = recentlyUsed.entries
                    .sortedBy { it.key.name }
                    .joinToString("; ") { (pattern, ids) ->
                        "${pattern.name}: ${ids.sorted().joinToString(", ")}"
                    }
                appendLine(
                    "Used in the previous accepted week (prefer a different exercise for the " +
                        "same movement pattern when an equally suitable option exists): $history"
                )
            }
            if (request.includeWorkoutData && request.recentWeights.isNotEmpty()) {
                val weights = request.recentWeights.joinToString("; ") {
                    "${it.exerciseId} ${isoDateUtc(it.performedAtMillis)}: " +
                        "${it.weightKg}kg x ${it.reps}"
                }
                appendLine(
                    "Recent working weights (use them to suggest a sensible weight for each " +
                        "exercise): $weights"
                )
                appendLine(
                    "Give every exercise a \"suggestedWeightKg\" number based on that history."
                )
            }
            val progressed = progressedWeights(request)
            if (progressed.isNotEmpty()) {
                appendLine(
                    "Progressed starting weights (already adjusted for progressive overload): " +
                        progressed
                )
                appendLine(
                    "Use these as \"suggestedWeightKg\" unless the history clearly disagrees."
                )
            }
        }

        return GeminiRequest(
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                responseSchema = planSchema(request)
            )
        )
    }

    private fun progressedWeights(request: PlanRequest): String {
        if (!request.includeWorkoutData) return ""
        return request.suggestedWeightsKg.entries
            .sortedBy { it.key }
            .joinToString("; ") { "${it.key}: ${it.value}kg" }
    }

    private fun planSchema(request: PlanRequest): GeminiSchema = GeminiSchema(
        type = "OBJECT",
        properties = mapOf(
            "days" to GeminiSchema(
                type = "ARRAY",
                minItems = request.daysPerWeek,
                maxItems = request.daysPerWeek,
                items = GeminiSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "focus" to GeminiSchema(
                            type = "STRING",
                            enum = SplitFocus.entries.map { it.name }
                        ),
                        "exercises" to GeminiSchema(
                            type = "ARRAY",
                            minItems = MIN_DAY_EXERCISES,
                            maxItems = MAX_DAY_EXERCISES,
                            items = GeminiSchema(
                                type = "OBJECT",
                                properties = exerciseSchemaProperties(request),
                                required = listOf("exerciseId", "sets", "reps")
                            )
                        )
                    ),
                    required = listOf("focus", "exercises")
                )
            )
        ),
        required = listOf("days")
    )

    private fun exerciseSchemaProperties(request: PlanRequest): Map<String, GeminiSchema> =
        buildMap {
            put("exerciseId", GeminiSchema(type = "STRING"))
            put("sets", GeminiSchema(type = "INTEGER"))
            put("reps", GeminiSchema(type = "INTEGER"))
            if (request.includeWorkoutData) {
                put("suggestedWeightKg", GeminiSchema(type = "NUMBER"))
            }
        }

    private companion object {
        const val MAX_RETRIES = 2
        const val BASE_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 8_000L
        const val MIN_DAY_EXERCISES = 4
        const val MAX_DAY_EXERCISES = 6

        val TRANSIENT_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)
    }
}
