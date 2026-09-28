package com.hydrafit.app.core.network

import kotlinx.serialization.Serializable

@Serializable
data class GeminiContent(val role: String? = null, val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String = "application/json",
    val temperature: Double? = null,
    val responseSchema: GeminiSchema? = null
)

@Serializable
data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val items: GeminiSchema? = null,
    val enum: List<String>? = null
)

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)
