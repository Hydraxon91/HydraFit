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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface GroqApiKeyProvider {
    fun groqApiKey(): String
}

class GroqWorkoutPlannerEngine(
    private val httpClient: HttpClient,
    private val catalog: ExerciseCatalog,
    private val apiKeyProvider: GroqApiKeyProvider,
    private val sanitizer: WeeklyPlanSanitizer,
    private val promptBuilder: GeminiWorkoutPlannerEngine
) : WorkoutPlannerEngine {
    override val id = PlannerEngineId.GROQ_API

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        val key = apiKeyProvider.groqApiKey()
        if (key.isBlank()) {
            throw failure(PlanFailureReason.INVALID_API_KEY, "Groq API key is not configured")
        }
        val exercises = catalog.all()
        PlannerCandidateEligibility.requireWorkouts(exercises, request)
        val ids = PlannerCandidateEligibility.candidates(exercises, request).map { it.id }
        val source = promptBuilder.buildRequest(request, ids)
        val sourceSchema = requireNotNull(source.generationConfig?.responseSchema)
        val schema = convertSchema(
            geminiJson.parseToJsonElement(geminiJson.encodeToString(sourceSchema))
        ).jsonObject
        val payload = GroqRequest(
            model = MODEL,
            messages = listOf(GroqMessage("user", source.contents.single().parts.single().text)),
            responseFormat = GroqResponseFormat(
                "json_schema",
                GroqJsonSchema("weekly_plan", true, schema)
            ),
            maxCompletionTokens = MAX_COMPLETION_TOKENS
        )

        var attempt = 0
        while (true) {
            val response = try {
                httpClient.post("$BASE_URL/chat/completions") {
                    header(HttpHeaders.Authorization, "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (timeout: HttpRequestTimeoutException) {
                throw failure(PlanFailureReason.TIMEOUT, "Groq request timed out", true)
            } catch (error: Exception) {
                throw failure(PlanFailureReason.NETWORK, "Could not reach Groq", true)
            }
            if (response.status.isSuccess()) return planFrom(response, ids, exercises, request)

            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            val status = response.status.value
            // A per-day quota will not clear within the backoff window, so it is not retried.
            val dailyQuota = status == 429 && isDailyQuota(body)
            val validationFailure = status == 400 && isJsonValidationFailure(body)
            val reason = when {
                dailyQuota -> PlanFailureReason.QUOTA_EXHAUSTED
                status == 429 -> PlanFailureReason.RATE_LIMITED
                status == 401 || status == 403 -> PlanFailureReason.INVALID_API_KEY
                status == 408 -> PlanFailureReason.TIMEOUT
                validationFailure -> PlanFailureReason.INVALID_RESPONSE
                status in 500..599 -> PlanFailureReason.SERVICE_UNAVAILABLE
                else -> PlanFailureReason.INVALID_REQUEST
            }
            val transient = !dailyQuota &&
                (status == 408 || status == 429 || status in 500..599)
            if (!transient || attempt >= MAX_RETRIES) {
                throw failure(reason, "Groq request failed with status $status", transient)
            }
            delay(retryDelayMillis(response, attempt))
            attempt++
        }
    }

    private suspend fun planFrom(
        response: io.ktor.client.statement.HttpResponse,
        ids: List<String>,
        exercises: List<com.hydrafit.app.core.domain.equipment.Exercise>,
        request: PlanRequest
    ): WeeklyPlan {
        val text = try {
            response.body<GroqResponse>().choices.firstOrNull()?.message?.content
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        } ?: throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq response contained no plan")
        val parsed = try {
            resolveExerciseIds(parseWeeklyPlan(text, id), ids, exercises)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq returned an invalid plan")
        }
        return sanitizer.sanitize(parsed, request)
            ?: throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq returned an unusable plan")
    }

    private fun isDailyQuota(body: String): Boolean {
        val normalized = body.lowercase()
        return "per day" in normalized ||
            "perday" in normalized ||
            "daily" in normalized ||
            "\"tpd\"" in normalized
    }

    private fun isJsonValidationFailure(body: String): Boolean = runCatching {
        geminiJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            ?.get("code")?.jsonPrimitive?.content == "json_validate_failed"
    }.getOrDefault(false)

    private fun retryDelayMillis(
        response: io.ktor.client.statement.HttpResponse,
        attempt: Int
    ): Long {
        val hint = response.headers[HttpHeaders.RetryAfter]
            ?.removeSuffix("s")
            ?.toDoubleOrNull()
            ?.let { (it * 1000).toLong() }
        if (hint != null) {
            if (hint > MAX_RETRY_HINT_MILLIS) {
                throw failure(
                    PlanFailureReason.RATE_LIMITED,
                    "Groq asked to retry after ${hint / 1_000}s, above the " +
                        "${MAX_RETRY_HINT_MILLIS / 1_000}s limit",
                    true
                )
            }
            return hint.coerceAtLeast(0L)
        }
        val exponential = (BASE_BACKOFF_MILLIS shl attempt).coerceAtMost(MAX_BACKOFF_MILLIS)
        val half = exponential / 2
        return half + Random.nextLong(half + 1)
    }

    private fun failure(reason: PlanFailureReason, message: String, transient: Boolean = false) =
        PlanGenerationException(transient, reason, message)

    /** Converts the Gemini-shaped schema into the strict JSON Schema Groq requires. */
    private fun convertSchema(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::convertSchema))
        is JsonObject -> JsonObject(
            buildMap {
                element.forEach { (key, value) ->
                    put(
                        key,
                        if (key == "type") {
                            JsonPrimitive(value.jsonPrimitive.content.lowercase())
                        } else {
                            convertSchema(value)
                        }
                    )
                }
                if (element["type"]?.jsonPrimitive?.content == "OBJECT") {
                    put("additionalProperties", JsonPrimitive(false))
                    val properties = element["properties"]?.jsonObject
                    if (properties != null) {
                        val required = (element["required"] as? JsonArray)
                            ?.map { it.jsonPrimitive.content }
                            .orEmpty()
                        val converted = get("properties")!!.jsonObject.toMutableMap()
                        (properties.keys - required.toSet()).forEach { key ->
                            val optional = converted.getValue(key).jsonObject.toMutableMap()
                            val type = optional["type"]
                            if (type != null) {
                                optional["type"] = JsonArray(listOf(type, JsonPrimitive("null")))
                            }
                            converted[key] = JsonObject(optional)
                        }
                        put("properties", JsonObject(converted))
                        put("required", JsonArray(properties.keys.map(::JsonPrimitive)))
                    }
                }
            }
        )

        else -> element
    }

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"
        const val MODEL = "openai/gpt-oss-120b"
        const val MAX_COMPLETION_TOKENS = 3000
        const val MAX_RETRIES = 2
        const val BASE_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 8_000L
        const val MAX_RETRY_HINT_MILLIS = 60_000L
    }
}
