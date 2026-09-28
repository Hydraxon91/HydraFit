package com.hydrafit.app.core.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.NoRepeatNgramConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

class LiteRtLmTextGenerator(
    private val context: Context,
    private val modelManager: AndroidOnDeviceModelManager
) : OnDeviceTextGenerator {

    private var conversation: Conversation? = null

    override fun isAvailable(): Boolean = modelManager.isInstalled()

    override fun generate(prompt: String, jsonSchema: String?): String = try {
        val active = conversation ?: createConversation().also { conversation = it }
        val message = send(active, prompt, jsonSchema)
        val text = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
        Log.d(TAG, "On-device output (${text.length} chars): ${text.take(MAX_LOGGED_CHARS)}")
        text
    } catch (failure: Throwable) {
        Log.e(TAG, "On-device generation failed", failure)
        throw failure
    }

    private fun send(conversation: Conversation, prompt: String, jsonSchema: String?): Message {
        if (jsonSchema == null) return conversation.sendMessage(prompt)
        return try {
            conversation.sendMessage(
                text = prompt,
                noRepeatNgramConfig = NoRepeatNgramConfig(NO_REPEAT_NGRAM_SIZE),
                responseFormat = ResponseFormat.json(jsonSchema)
            )
        } catch (failure: Exception) {
            Log.w(TAG, "Constrained JSON generation unavailable; retrying without it", failure)
            conversation.sendMessage(prompt)
        }
    }

    private fun createConversation(): Conversation {
        Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)
        val modelPath = modelManager.modelPath()
        Log.i(
            TAG,
            "Initializing on-device engine (model ${File(modelPath).length()} bytes, backend CPU)"
        )
        val engine = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path
            )
        )
        engine.initialize()
        Log.i(TAG, "On-device engine initialized")
        return engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = SAMPLER_TOP_K,
                    topP = SAMPLER_TOP_P,
                    temperature = SAMPLER_TEMPERATURE,
                    seed = SAMPLER_SEED
                ),
                enableResponseFormat = true
            )
        )
    }

    private companion object {
        const val TAG = "LiteRtLmTextGenerator"
        const val MAX_LOGGED_CHARS = 4_000
        const val NO_REPEAT_NGRAM_SIZE = 3
        const val SAMPLER_TOP_K = 40
        const val SAMPLER_TOP_P = 0.95
        const val SAMPLER_TEMPERATURE = 0.2
        const val SAMPLER_SEED = 0
    }
}
