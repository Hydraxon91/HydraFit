package com.hydrafit.app

import java.io.File

/**
 * Deletes model files left behind by the retired on-device planner.
 *
 * The feature no longer reads them, so keeping a multi-hundred-megabyte weight file only wastes
 * storage. Only the exact app-managed filenames are touched; any other file in app storage is
 * left alone. Missing files are not an error, so the cleanup is idempotent.
 */
class StaleOnDeviceModelCleanup(private val filesDir: File) {

    fun run(): List<String> = MODEL_FILES.filter { name ->
        val file = File(filesDir, name)
        file.isFile && file.delete()
    }

    private companion object {
        val MODEL_FILES = listOf(
            "on_device_llm.litertlm",
            "on_device_llm.target",
            // The retired importer's half-written temporary weight file.
            "on_device_llm.litertlm.tmp"
        )
    }
}
