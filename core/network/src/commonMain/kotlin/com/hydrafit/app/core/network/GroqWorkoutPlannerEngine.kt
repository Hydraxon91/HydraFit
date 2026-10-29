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
            throw failure(
                PlanFailureReason.INVALID_API_KEY,
                "Groq API key is not configured"
            )
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
            model = "openai/gpt-oss-120b",
            messages = listOf(GroqMessage("user", source.contents.single().parts.single().text)),
            responseFormat = GroqResponseFormat(
                "json_schema",
                GroqJsonSchema("weekly_plan", true, schema)
            ),
            maxCompletionTokens = 3000
        )
        val response = try {
            httpClient.post("https://api.groq.com/openai/v1/chat/completions") {
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
        if (!response.status.isSuccess()) {
            val status = response.status.value
            val validationFailure = if (status == 400) {
                val body = response.bodyAsText()
                runCatching {
                    geminiJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                        ?.get("code")?.jsonPrimitive?.content == "json_validate_failed"
                }.getOrDefault(false)
            } else {
                false
            }
            val reason = when (status) {
                401, 403 -> PlanFailureReason.INVALID_API_KEY
                429 -> PlanFailureReason.RATE_LIMITED
                408 -> PlanFailureReason.TIMEOUT
                400 -> if (validationFailure) {
                    PlanFailureReason.INVALID_RESPONSE
                } else {
                    PlanFailureReason.INVALID_REQUEST
                }
                in 500..599 -> PlanFailureReason.SERVICE_UNAVAILABLE
                else -> PlanFailureReason.INVALID_REQUEST
            }
            throw failure(
                reason,
                "Groq request failed with status $status",
                status == 429 || status >= 500
            )
        }
        val text = try {
            response.body<GroqResponse>().choices.firstOrNull()?.message?.content
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        } ?: throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq response contained no plan")
        val parsed = try {
            parseWeeklyPlan(text, id)
        } catch (error: Exception) {
            throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq returned an invalid plan")
        }
        return sanitizer.sanitize(parsed, request)
            ?: throw failure(PlanFailureReason.INVALID_RESPONSE, "Groq returned an unusable plan")
    }

    private fun failure(reason: PlanFailureReason, message: String, transient: Boolean = false) =
        PlanGenerationException(transient, reason, message)

    private fun convertSchema(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::convertSchema))
        is JsonObject -> JsonObject(
            buildMap {
                element.forEach { (key, value) ->
                    put(
                        key,
                        if (key ==
                            "type"
                        ) {
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
                        val required = (element["required"] as? JsonArray)?.map {
                            it.jsonPrimitive.content
                        }.orEmpty()
                        val converted = get("properties")!!.jsonObject.toMutableMap()
                        (properties.keys - required.toSet()).forEach { key ->
                            val optional = converted.getValue(key).jsonObject.toMutableMap()
                            val type = optional["type"]
                            if (type !=
                                null
                            ) {
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
}
