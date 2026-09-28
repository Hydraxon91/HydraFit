package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanRequest
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.parseWeeklyPlan
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class GeminiWorkoutPlannerEngine(
    private val httpClient: HttpClient,
    private val config: GeminiConfig,
    private val catalog: ExerciseCatalog
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.GEMINI_API

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan {
        require(config.apiKey.isNotBlank()) { "Gemini API key is not configured" }

        val response = httpClient.post("${config.baseUrl}/models/${config.model}:generateContent") {
            header("x-goog-api-key", config.apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildRequest(request))
        }
        check(response.status.isSuccess()) {
            "Gemini request failed with status ${response.status}"
        }

        val text = response.body<GeminiResponse>()
            .candidates.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?: error("Gemini response contained no content")

        return parseWeeklyPlan(text, PlannerEngineId.GEMINI_API)
    }

    private suspend fun buildRequest(request: PlanRequest): GeminiRequest {
        val equipment = request.availableEquipment.joinToString(", ") { it.name }
        val fatigue = request.muscleFatigue.entries.joinToString(", ") {
            "${it.key.name}=${it.value}"
        }
        val exerciseIds = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .joinToString(", ") { it.id }

        val prompt = buildString {
            appendLine("You are a strength coach. Build a weekly workout plan.")
            appendLine("Days per week: ${request.daysPerWeek}")
            appendLine("Available equipment: $equipment")
            appendLine("Current muscle fatigue (0.0-1.0): $fatigue")
            appendLine("Choose ONLY exerciseId values from this list: $exerciseIds")
            appendLine("Prefer exercises whose muscles are less fatigued.")
        }

        return GeminiRequest(
            contents = listOf(
                GeminiContent(role = "user", parts = listOf(GeminiPart(prompt)))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.4,
                responseSchema = planSchema()
            )
        )
    }

    private fun planSchema(): GeminiSchema = GeminiSchema(
        type = "OBJECT",
        properties = mapOf(
            "days" to GeminiSchema(
                type = "ARRAY",
                items = GeminiSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "focus" to GeminiSchema(
                            type = "STRING",
                            enum = SplitFocus.entries.map { it.name }
                        ),
                        "exercises" to GeminiSchema(
                            type = "ARRAY",
                            items = GeminiSchema(
                                type = "OBJECT",
                                properties = mapOf(
                                    "exerciseId" to GeminiSchema(type = "STRING"),
                                    "sets" to GeminiSchema(type = "INTEGER"),
                                    "reps" to GeminiSchema(type = "INTEGER")
                                ),
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
}
