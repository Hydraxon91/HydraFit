package com.hydrafit.app.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GroqDtosTest {
    @Test
    fun requestUsesGroqFieldNamesAndStrictSchema() {
        val request = GroqRequest(
            model = "test-model",
            messages = listOf(GroqMessage("user", "synthetic")),
            responseFormat = GroqResponseFormat(
                type = "json_schema",
                jsonSchema = GroqJsonSchema(
                    name = "weekly_plan",
                    strict = true,
                    schema = buildJsonObject { put("type", "object") }
                )
            ),
            maxCompletionTokens = 100
        )
        val json = geminiJson.parseToJsonElement(geminiJson.encodeToString(request)).jsonObject
        val format = json.getValue("response_format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        assertEquals(
            "true",
            format.getValue("json_schema").jsonObject.getValue("strict").toString()
        )
        assertEquals("100", json.getValue("max_completion_tokens").toString())
    }

    @Test
    fun responseIgnoresProviderMetadata() {
        val response = geminiJson.decodeFromString<GroqResponse>(
            """{"choices":[{"message":{"role":"assistant","content":"{}"}}],"usage":{}}"""
        )
        assertEquals("{}", response.choices.single().message.content)
        assertTrue(response.choices.isNotEmpty())
    }
}
