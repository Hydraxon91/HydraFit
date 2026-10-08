package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlanGenerationException
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.PlannerExerciseCounts
import com.hydrafit.app.core.domain.engine.PlannerPromptFragments
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.SplitResolver
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlin.coroutines.cancellation.CancellationException
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
        if (apiKey.isBlank()) {
            throw PlanGenerationException(
                transient = false,
                reason = PlanFailureReason.INVALID_API_KEY,
                message = "Gemini API key is not configured"
            )
        }
        val catalogExercises = catalog.all()
        val availableIds = catalogExercises
            .filter {
                it.isAvailableWith(request.availableEquipment) &&
                    it.id !in request.excludedExerciseIds
            }
            .map { it.id }
        val payload = buildRequest(request, availableIds)
        val url = "${config.baseUrl}/models/${config.model}:generateContent"

        var attempt = 0
        while (true) {
            val response = try {
                httpClient.post(url) {
                    header("x-goog-api-key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (timeout: HttpRequestTimeoutException) {
                throw PlanGenerationException(
                    transient = true,
                    reason = PlanFailureReason.TIMEOUT,
                    message = "Gemini request timed out"
                )
            } catch (failure: Exception) {
                throw PlanGenerationException(
                    transient = true,
                    reason = PlanFailureReason.NETWORK,
                    message = failure.message ?: "Could not reach the Gemini API"
                )
            }
            if (response.status.isSuccess()) {
                val text = response.body<GeminiResponse>()
                    .candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?: throw PlanGenerationException(
                        transient = false,
                        reason = PlanFailureReason.INVALID_RESPONSE,
                        message = "Gemini response contained no content"
                    )
                val plan = normalizeExerciseIds(
                    parseWeeklyPlan(text, PlannerEngineId.GEMINI_API),
                    availableIds,
                    catalogExercises
                )
                return sanitizer.sanitize(plan, request) ?: fallback.generatePlan(request)
            }

            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val reason = failureReason(response.status, body)
            // A daily-quota 429 will not clear within the backoff window, so it is not retried.
            val transient = reason != PlanFailureReason.QUOTA_EXHAUSTED &&
                response.status.isTransient()
            if (!transient || attempt >= MAX_RETRIES) {
                throw PlanGenerationException(
                    transient = transient,
                    reason = reason,
                    message = failureMessage(response.status, body)
                )
            }

            delay(retryDelayMillis(response, body, attempt))
            attempt++
        }
    }

    private fun failureReason(status: HttpStatusCode, body: String): PlanFailureReason = when {
        status.value == 429 && isDailyQuota(body) -> PlanFailureReason.QUOTA_EXHAUSTED
        status.value == 429 -> PlanFailureReason.RATE_LIMITED
        status.value == 408 -> PlanFailureReason.TIMEOUT
        status.value == 401 || status.value == 403 -> PlanFailureReason.INVALID_API_KEY
        status.value in 500..599 -> PlanFailureReason.SERVICE_UNAVAILABLE
        status.value in 400..499 -> PlanFailureReason.INVALID_REQUEST
        else -> PlanFailureReason.UNKNOWN
    }

    private fun isDailyQuota(body: String): Boolean {
        val normalized = body.lowercase()
        return "perday" in normalized.replace(" ", "") ||
            "per day" in normalized ||
            "daily limit" in normalized
    }

    /** Maps model-returned ids to catalog ids, tolerating a returned name or different casing. */
    private fun normalizeExerciseIds(
        plan: WeeklyPlan,
        availableIds: List<String>,
        catalogExercises: List<Exercise>
    ): WeeklyPlan {
        if (availableIds.isEmpty()) return plan
        val byId = availableIds.associateBy { it.lowercase() }
        val byName = catalogExercises.associateBy { it.name.lowercase() }
        return plan.copy(
            days = plan.days.map { day ->
                day.copy(
                    exercises = day.exercises.map { planned ->
                        val raw = planned.exerciseId
                        val resolved = byId[raw.lowercase()]
                            ?: byName[raw.lowercase()]?.id
                            ?: raw
                        planned.copy(exerciseId = resolved)
                    }
                )
            }
        )
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
            ?: return backoffMillis(attempt)
        if (hint > MAX_RETRY_HINT_MILLIS) {
            throw PlanGenerationException(
                transient = true,
                reason = PlanFailureReason.RATE_LIMITED,
                message = "Gemini asked to retry after ${hint / 1_000}s, above the " +
                    "${MAX_RETRY_HINT_MILLIS / 1_000}s limit"
            )
        }
        return hint.coerceAtLeast(0L)
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

    private suspend fun buildRequest(
        request: PlanRequest,
        availableIds: List<String>
    ): GeminiRequest {
        val exerciseIds = availableIds.joinToString(", ")
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
            appendLine(PlannerPromptFragments.equipmentLine(request))
            PlannerPromptFragments.equipmentCapsLine(request)?.let { appendLine(it) }
            appendLine(PlannerPromptFragments.fatigueLine(request, "Current muscle fatigue"))
            appendLine(PlannerPromptFragments.periodizationLine(request))
            PlannerPromptFragments.deloadInstruction(request)?.let { appendLine(it) }
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine(
                "Give every day ${PlannerExerciseCounts.TARGET_MIN_PER_DAY} to " +
                    "${PlannerExerciseCounts.TARGET_MAX_PER_DAY} exercises."
            )
            appendLine(
                "Give each day a different focus from the schedule above, and do not reuse a " +
                    "compound lift across days; isolation exercises may repeat."
            )
            appendLine(
                "Use ${request.setsPerExercise} sets for compound lifts and " +
                    "${request.accessorySetsPerExercise} sets for accessory exercises."
            )
            appendLine(PlannerPromptFragments.volumeRepsGuidance(request))
            appendLine(PlannerPromptFragments.directArmCoverageGuidance(request))
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
            PlannerPromptFragments.recentWeightsList(request)?.let { weights ->
                appendLine(
                    "Recent working weights (use them to suggest a sensible weight for each " +
                        "exercise): $weights"
                )
                appendLine(
                    "Give a \"suggestedWeightKg\" number only for exercises whose resistance is " +
                        "external weight; omit it for bodyweight and bodyweight-plus-added-load " +
                        "movements."
                )
            }
            PlannerPromptFragments.progressedWeightsList(request)?.let { progressed ->
                appendLine(
                    "Progressed starting weights (already adjusted for progressive overload): " +
                        progressed
                )
                appendLine(PlannerPromptFragments.PROGRESSED_WEIGHTS_NOTE)
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
                            minItems = PlannerExerciseCounts.TARGET_MIN_PER_DAY,
                            maxItems = PlannerExerciseCounts.TARGET_MAX_PER_DAY,
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
            // The prompt lists the valid ids and normalizeExerciseIds repairs returned names; an
            // `enum` here is not used because it made Gemini reject the whole request (400).
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

        /**
         * Ceiling for an explicit server retry hint (`Retry-After` or `RetryInfo.retryDelay`).
         * A hint above this is treated as non-transient at this scale; automatic backoff stays
         * capped at [MAX_BACKOFF_MILLIS].
         */
        const val MAX_RETRY_HINT_MILLIS = 60_000L

        val TRANSIENT_STATUS_CODES = setOf(408, 429, 500, 502, 503, 504)
    }
}
