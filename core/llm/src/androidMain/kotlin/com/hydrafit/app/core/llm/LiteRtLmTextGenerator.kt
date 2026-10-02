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
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.userdata.llm.OnDeviceModelTarget
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

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
    override fun generate(
        prompt: String,
        jsonSchema: String?,
        onProgress: (OnDevicePlanProgress) -> Unit
    ): String = try {
        Log.d(TAG, "On-device prompt (${prompt.length} chars): ${prompt.take(MAX_LOGGED_CHARS)}")
        val conversation = activeEngine().createConversation(conversationConfig())
        try {
            val text = stream(conversation, prompt, jsonSchema, onProgress)
            Log.d(TAG, "On-device output (${text.length} chars): ${text.take(MAX_LOGGED_CHARS)}")
            text
        } finally {
            conversation.close()
        }
    } catch (failure: Throwable) {
        Log.e(TAG, "On-device generation failed", failure)
        throw failure
    }

    /**
     * Streams the reply so the UI can show live tokens and tokens/second. The callback may deliver
     * either running or incremental text, so the longer of the two is kept; the token count prefers
     * the runtime counter and falls back to a character estimate while it is still zero.
     */
    private fun stream(
        conversation: Conversation,
        prompt: String,
        jsonSchema: String?,
        onProgress: (OnDevicePlanProgress) -> Unit
    ): String {
        val builder = StringBuilder()
        val failure = AtomicReference<Throwable?>(null)
        val done = CountDownLatch(1)
        val startedNanos = System.nanoTime()
        val constrained = jsonSchema != null && constrainedSupported
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                val chunk = readText(message)
                if (chunk.length >= builder.length) {
                    builder.setLength(0)
                    builder.append(chunk)
                } else {
                    builder.append(chunk)
                }
                val nativeTokens = runCatching { conversation.getTokenCount() }.getOrDefault(0)
                val tokens = maxOf(nativeTokens, builder.length / CHARS_PER_TOKEN)
                val seconds = (System.nanoTime() - startedNanos) / NANOS_PER_SECOND
                onProgress(
                    OnDevicePlanProgress(
                        tokensGenerated = tokens,
                        expectedTokens = MAX_OUTPUT_TOKENS,
                        tokensPerSecond = if (seconds > 0.0) tokens / seconds else 0.0
                    )
                )
            }

            override fun onDone() {
                done.countDown()
            }

            override fun onError(error: Throwable) {
                if (constrained) {
                    constrainedSupported = false
                    Log.w(
                        TAG,
                        "Constrained JSON generation failed; disabling it for this session",
                        error
                    )
                }
                failure.set(error)
                done.countDown()
            }
        }
        conversation.sendMessageAsync(
            prompt,
            callback,
            responseFormat = if (constrained) ResponseFormat.json(jsonSchema!!) else null
        )
        done.await()
        failure.get()?.let { throw it }
        return builder.toString()
    }

    private fun readText(message: Message): String = message.contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString(separator = "") { it.text }

    private fun activeEngine(): Engine {
        val modelFile = File(modelManager.modelPath())
        val key = EngineKey(modelFile.path, modelFile.length(), modelFile.lastModified())
        val current = engine
        if (current != null && engineKey == key) return current

        // Drop the cached engine before creating a new one. If the model changed, close the old
        // one; if creation then fails, no stale reference survives to trip the next call (LiteRT's
        // Engine.close() throws once the engine was never initialized or already closed).
        engine = null
        engineKey = null
        if (current != null) {
            runCatching { current.close() }
                .onFailure { Log.w(TAG, "Could not close the previous on-device engine", it) }
        }

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
                maxNumTokens = MAX_NUM_TOKENS,
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
        enableResponseFormat = true,
        // Bound the reply so a runaway generation cannot grow without limit. The plan JSON is small
        // once sets/reps are dropped, so this is comfortably above a full week.
        maxOutputToken = MAX_OUTPUT_TOKENS
    )

    private data class EngineKey(val path: String, val size: Long, val modified: Long)

    private companion object {
        const val TAG = "LiteRtLmTextGenerator"

        /**
         * Total context (prompt + reply). The native default is small enough that a full-week plan
         * prompt plus its JSON reply overruns it; the model then stops mid-array and the reply is
         * unparseable. Raising it gives the constrained grammar room to finish.
         */
        const val MAX_NUM_TOKENS = 4_096
        const val MAX_OUTPUT_TOKENS = 2_048
        const val MAX_LOGGED_CHARS = 4_000

        /** Rough characters per token, used only while the runtime token counter reads zero. */
        const val CHARS_PER_TOKEN = 4
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
