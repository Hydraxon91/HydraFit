package com.hydrafit.app.core.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

class LiteRtLmTextGenerator(
    private val context: Context,
    private val modelAssetPath: String = ON_DEVICE_LLM_ASSET
) : OnDeviceTextGenerator {

    private var conversation: Conversation? = null

    override fun isAvailable(): Boolean =
        runCatching { context.assets.open(modelAssetPath).close() }.isSuccess

    override fun generate(prompt: String): String {
        val active = conversation ?: createConversation().also { conversation = it }
        val message = active.sendMessage(prompt)
        return message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
    }

    private fun createConversation(): Conversation {
        val engineConfig = EngineConfig(
            modelPath = ensureModelFile().absolutePath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path
        )
        val engine = Engine(engineConfig)
        engine.initialize()
        return engine.createConversation()
    }

    private fun ensureModelFile(): File {
        val target = File(context.filesDir, MODEL_FILE_NAME)
        if (!target.exists()) {
            context.assets.open(modelAssetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private companion object {
        const val MODEL_FILE_NAME = "on_device_llm.litertlm"
    }
}
