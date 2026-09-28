package com.hydrafit.app.core.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

@Suppress("DEPRECATION")
class MediaPipeLlmTextGenerator(
    private val context: Context,
    private val modelAssetPath: String = ON_DEVICE_LLM_ASSET,
    private val maxTokens: Int = 1024
) : OnDeviceTextGenerator {

    private var inference: LlmInference? = null

    override fun isAvailable(): Boolean =
        runCatching { context.assets.open(modelAssetPath).close() }.isSuccess

    override fun generate(prompt: String): String {
        val llm = inference ?: createInference().also { inference = it }
        return llm.generateResponse(prompt)
    }

    private fun createInference(): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(ensureModelFile().absolutePath)
            .setMaxTokens(maxTokens)
            .build()
        return LlmInference.createFromOptions(context, options)
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
        const val MODEL_FILE_NAME = "on_device_llm.task"
    }
}
