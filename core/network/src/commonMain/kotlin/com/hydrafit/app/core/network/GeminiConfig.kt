package com.hydrafit.app.core.network

data class GeminiConfig(
    val model: String = "gemini-3.1-flash-lite",
    val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta"
)

fun interface ApiKeyProvider {
    fun geminiApiKey(): String
}
