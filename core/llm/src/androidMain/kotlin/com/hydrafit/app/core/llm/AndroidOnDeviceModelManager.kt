package com.hydrafit.app.core.llm

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.IOException

class AndroidOnDeviceModelManager(private val context: Context) {

    private val modelFile = File(context.filesDir, MODEL_FILE_NAME)
    private val tempFile = File(context.filesDir, "$MODEL_FILE_NAME.tmp")

    fun isInstalled(): Boolean = modelFile.exists() && modelFile.length() > 0

    fun remove() {
        runLogged("remove") {
            modelFile.delete()
            tempFile.delete()
        }
    }

    /**
     * Copies the picked document to a temporary file and only replaces the current
     * model once the copy succeeded, so a failed import cannot destroy a working model.
     */
    fun importFromUri(uri: Uri) {
        runLogged("import") { importFromUriInternal(uri) }
    }

    private fun importFromUriInternal(uri: Uri) {
        val declaredSize = querySize(uri)
        val required = (declaredSize ?: 0L) + (if (modelFile.exists()) modelFile.length() else 0L)
        if (declaredSize != null && required > context.filesDir.usableSpace) {
            throw InsufficientStorageException(
                "Importing the model needs $required bytes but only " +
                    "${context.filesDir.usableSpace} are available"
            )
        }

        val input = openInput(uri)
        tempFile.delete()
        input.use { source ->
            try {
                tempFile.outputStream().use { target -> source.copyTo(target) }
            } catch (failure: IOException) {
                tempFile.delete()
                throw failure.toImportFailure()
            }
        }

        if (tempFile.length() <= 0L) {
            tempFile.delete()
            throw ModelSourceUnreadableException("The selected model file is empty")
        }

        if (!replaceModelWith(tempFile)) {
            tempFile.delete()
            throw IOException("Could not store the imported model")
        }
    }

    fun modelPath(): String = modelFile.absolutePath

    private inline fun runLogged(operation: String, block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            Log.e(TAG, "On-device model $operation failed", failure)
            throw failure
        }
    }

    private fun openInput(uri: Uri): java.io.InputStream {
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (failure: IOException) {
            throw ModelSourceUnreadableException("Could not open the selected file")
        } catch (failure: SecurityException) {
            throw ModelSourceUnreadableException("Could not read the selected file")
        }
        return stream
            ?: throw ModelSourceUnreadableException("Could not open the selected file")
    }

    private fun replaceModelWith(source: File): Boolean {
        if (source.renameTo(modelFile)) return true
        modelFile.delete()
        return source.renameTo(modelFile)
    }

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }.getOrNull()

    private fun IOException.toImportFailure(): Exception = when {
        message?.contains("ENOSPC", ignoreCase = true) == true ||
            message?.contains("No space left", ignoreCase = true) == true ->
            InsufficientStorageException("Not enough storage to import the model")

        else -> this
    }

    companion object {
        const val MODEL_FILE_NAME = "on_device_llm.litertlm"
        private const val TAG = "OnDeviceModel"
    }
}

class InsufficientStorageException(message: String) : Exception(message)

class ModelSourceUnreadableException(message: String) : Exception(message)
