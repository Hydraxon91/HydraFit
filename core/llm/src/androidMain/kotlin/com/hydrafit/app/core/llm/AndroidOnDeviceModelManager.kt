package com.hydrafit.app.core.llm

import android.content.Context
import android.net.Uri
import java.io.File

class AndroidOnDeviceModelManager(private val context: Context) {

    private val modelFile = File(context.filesDir, MODEL_FILE_NAME)

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() > 0

    fun remove() {
        if (modelFile.exists()) {
            modelFile.delete()
        }
    }

    fun importFromUri(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open the selected file")
        input.use { source ->
            modelFile.outputStream().use { target -> source.copyTo(target) }
        }
    }

    fun modelPath(): String = modelFile.absolutePath

    companion object {
        const val MODEL_FILE_NAME = "on_device_llm.litertlm"
    }
}
