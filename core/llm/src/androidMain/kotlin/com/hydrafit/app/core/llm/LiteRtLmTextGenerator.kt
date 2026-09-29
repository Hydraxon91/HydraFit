package com.hydrafit.app.core.llm

import android.content.Context
import android.system.Os
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget
import java.io.File

/**
 * LiteRT-LM keeps the full message history inside a [Conversation], and a failed native call
 * can leave a dangling user turn behind. Reusing such a conversation makes every later call
 * fail with "Conversation roles must alternate". So the engine is cached, but each generation
 * gets a fresh conversation that is closed afterwards.
 */
class LiteRtLmTextGenerator(
    private val context: Context,
    private val modelManager: AndroidOnDeviceModelManager,
    private val sampler: OnDeviceSampler = OnDeviceSampler()
) : OnDeviceTextGenerator {

    private var engine: Engine? = null
    private var engineKey: EngineKey? = null
    private var constrainedSupported = true

    override fun isAvailable(): Boolean = modelManager.isInstalled()

    @Synchronized
    override fun generate(prompt: String, jsonSchema: String?): String = try {
        Log.d(TAG, "On-device prompt (${prompt.length} chars): ${prompt.take(MAX_LOGGED_CHARS)}")
        val conversation = activeEngine().createConversation(conversationConfig())
        try {
            val text = readText(send(conversation, prompt, jsonSchema))
            Log.d(TAG, "On-device output (${text.length} chars): ${text.take(MAX_LOGGED_CHARS)}")
            text
        } finally {
            conversation.close()
        }
    } catch (failure: Throwable) {
        Log.e(TAG, "On-device generation failed", failure)
        throw failure
    }

    private fun send(conversation: Conversation, prompt: String, jsonSchema: String?): Message {
        if (jsonSchema == null || !constrainedSupported) return conversation.sendMessage(prompt)
        return try {
            conversation.sendMessage(
                text = prompt,
                responseFormat = ResponseFormat.json(jsonSchema)
            )
        } catch (failure: Exception) {
            // Never retry on the same conversation; the next attempt builds a fresh one.
            constrainedSupported = false
            Log.w(TAG, "Constrained JSON generation failed; disabling it for this session", failure)
            throw failure
        }
    }

    private fun readText(message: Message): String = message.contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString(separator = "") { it.text }

    private fun activeEngine(): Engine {
        val modelFile = File(modelManager.modelPath())
        val key = EngineKey(modelFile.path, modelFile.length(), modelFile.lastModified())
        val current = engine
        if (current != null && engineKey == key) return current

        current?.close()
        constrainedSupported = true
        val created = createEngine(key)
        engine = created
        engineKey = key
        return created
    }

    /**
     * Tries the backends best suited to the installed model in order and returns the first engine
     * that initializes. NPU packs prefer NPU then GPU then CPU; the portable pack skips NPU (it has
     * no NPU graphs). This matters because NPU initialization can still fail on a target SoC when
     * the bundled runtime is mismatched, so a graceful chain keeps the engine usable.
     */
    private fun createEngine(key: EngineKey): Engine {
        Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)
        var lastFailure: Throwable? = null
        for (backend in backendChain(modelManager.modelTarget())) {
            try {
                return initializeEngine(key, backend)
            } catch (failure: Throwable) {
                lastFailure = failure
                Log.w(TAG, "Backend ${backend.name} failed to initialize", failure)
            }
        }
        throw lastFailure ?: IllegalStateException("No LiteRT-LM backend could be initialized")
    }

    private fun initializeEngine(key: EngineKey, backend: Backend): Engine {
        Log.i(TAG, "Initializing on-device engine (model ${key.size} bytes, ${backend.name})")
        if (backend is Backend.NPU) configureNpuLibraryPath()
        val created = Engine(
            EngineConfig(
                modelPath = key.path,
                backend = backend,
                cacheDir = context.cacheDir.path
            )
        )
        try {
            created.initialize()
        } catch (failure: Throwable) {
            runCatching { created.close() }
            throw failure
        }
        Log.i(TAG, "On-device engine initialized on ${backend.name}")
        return created
    }

    /** Resolves the Qualcomm NPU runtime libraries bundled in the app's native library directory. */
    private fun configureNpuLibraryPath() {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
        runCatching {
            Os.setenv("LD_LIBRARY_PATH", nativeLibraryDir, true)
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibraryDir, true)
        }.onFailure { Log.w(TAG, "Could not set the NPU library path", it) }
    }

    private fun backendChain(target: OnDeviceModelTarget): List<Backend> = when (target) {
        OnDeviceModelTarget.NPU -> listOf(
            Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
            Backend.GPU(),
            Backend.CPU()
        )
        OnDeviceModelTarget.CPU_GPU -> listOf(Backend.GPU(), Backend.CPU())
    }

    private fun conversationConfig(): ConversationConfig = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = sampler.topK,
            topP = sampler.topP,
            temperature = sampler.temperature,
            // A fresh seed per generation so repeated generations are not identical.
            seed = sampler.randomSeed()
        ),
        enableResponseFormat = true
    )

    private data class EngineKey(val path: String, val size: Long, val modified: Long)

    private companion object {
        const val TAG = "LiteRtLmTextGenerator"
        const val MAX_LOGGED_CHARS = 4_000
    }
}
