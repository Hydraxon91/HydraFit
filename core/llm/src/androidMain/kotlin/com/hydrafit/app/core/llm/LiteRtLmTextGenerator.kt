package com.hydrafit.app.core.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import java.io.File

class LiteRtLmTextGenerator(
    private val context: Context,
    private val modelManager: AndroidOnDeviceModelManager
) : OnDeviceTextGenerator {

    private var conversation: Conversation? = null

    override fun isAvailable(): Boolean = modelManager.isInstalled()

    override fun generate(prompt: String): String = try {
        val active = conversation ?: createConversation().also { conversation = it }
        val message = active.sendMessage(prompt)
        val text = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
        Log.d(TAG, "On-device output (${text.length} chars): ${text.take(MAX_LOGGED_CHARS)}")
        text
    } catch (failure: Throwable) {
        Log.e(TAG, "On-device generation failed", failure)
        throw failure
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
        return engine.createConversation()
    }

    private companion object {
        const val TAG = "LiteRtLmTextGenerator"
        const val MAX_LOGGED_CHARS = 4_000
    }
}
