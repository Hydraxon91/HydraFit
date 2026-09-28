package com.hydrafit.app.core.network

data class GeminiConfig(
    val apiKey: String,
    val model: String = "gemini-2.5-flash",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
)

fun interface ApiKeyProvider {
    fun geminiApiKey(): String
}
