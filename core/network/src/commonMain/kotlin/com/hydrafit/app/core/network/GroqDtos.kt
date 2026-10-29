package com.hydrafit.app.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GroqMessage(val role: String, val content: String)

@Serializable
data class GroqJsonSchema(val name: String, val strict: Boolean, val schema: JsonObject)

@Serializable
data class GroqResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: GroqJsonSchema
)

@Serializable
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    @SerialName("response_format") val responseFormat: GroqResponseFormat,
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int
)

@Serializable
data class GroqChoice(val message: GroqMessage)

@Serializable
data class GroqResponse(val choices: List<GroqChoice> = emptyList())
