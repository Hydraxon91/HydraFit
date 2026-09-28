package com.hydrafit.app.core.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

class LiteRtLmTextGenerator(
    private val context: Context,
    private val modelManager: AndroidOnDeviceModelManager
) : OnDeviceTextGenerator {

    private var conversation: Conversation? = null

    override fun isAvailable(): Boolean = modelManager.isInstalled()

    override fun generate(prompt: String): String {
        val active = conversation ?: createConversation().also { conversation = it }
        val message = active.sendMessage(prompt)
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
    }

    private fun createConversation(): Conversation {
        val engineConfig = EngineConfig(
            modelPath = modelManager.modelPath(),
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path
        )
        val engine = Engine(engineConfig)
        engine.initialize()
        return engine.createConversation()
    }
}
