package com.hydrafit.app.core.exercisecontent

/**
 * Builds the link behind "Watch a demo". It is a YouTube search for the exercise name plus
 * "proper form", opened outside the app only when the person taps it, so nothing is requested until
 * then and HydraFit hosts and embeds no video.
 */
object ExerciseDemoLink {
    private const val SEARCH_URL = "https://www.youtube.com/results?search_query="
    private const val SEARCH_SUFFIX = " proper form"

    private const val WATCH_URL = "https://www.youtube.com/watch?v="

    /**
     * The hand-picked video for [exerciseId] when one exists, otherwise the search for
     * [exerciseName]. Null only when there is no curated video and the name is blank.
     */
    fun urlFor(exerciseId: String?, exerciseName: String): String? {
        val videoId = exerciseId?.let { ExerciseDemoVideos.videoIdByExerciseId[it] }
        return if (videoId != null) WATCH_URL + videoId else searchUrl(exerciseName)
    }

    /** Null for a blank name, which has nothing meaningful to search for. */
    fun searchUrl(exerciseName: String): String? {
        val name = exerciseName.trim()
        if (name.isEmpty()) return null
        return SEARCH_URL + percentEncode(name + SEARCH_SUFFIX)
    }

    /** RFC 3986 unreserved characters stay; every other UTF-8 byte becomes `%XX`. */
    private fun percentEncode(text: String): String = buildString {
        text.encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            val char = value.toChar()
            if (value < ASCII_LIMIT && (char.isLetterOrDigit() || char in "-_.~")) {
                append(char)
            } else {
                append('%')
                append(HEX[value shr HEX_SHIFT])
                append(HEX[value and HEX_MASK])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
    private const val BYTE_MASK = 0xFF
    private const val ASCII_LIMIT = 0x80
    private const val HEX_SHIFT = 4
    private const val HEX_MASK = 0x0F
}
