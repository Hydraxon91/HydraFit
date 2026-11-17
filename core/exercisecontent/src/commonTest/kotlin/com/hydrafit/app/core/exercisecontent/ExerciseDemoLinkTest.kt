package com.hydrafit.app.core.exercisecontent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseDemoLinkTest {

    private val base = "https://www.youtube.com/results?search_query="

    @Test
    fun searchesForTheNameWithProperFormAndEncodesSpaces() {
        assertEquals(
            base + "Lat%20Pulldown%20proper%20form",
            ExerciseDemoLink.searchUrl("Lat Pulldown")
        )
    }

    @Test
    fun trimsTheNameAndRejectsABlankOne() {
        assertEquals(base + "Squat%20proper%20form", ExerciseDemoLink.searchUrl("  Squat \n"))
        assertNull(ExerciseDemoLink.searchUrl(""))
        assertNull(ExerciseDemoLink.searchUrl("   "))
    }

    @Test
    fun encodesReservedCharactersSoTheNameCannotChangeTheQuery() {
        val url = requireNotNull(ExerciseDemoLink.searchUrl("A&B=C#D?E/F+G%H"))

        assertEquals(base + "A%26B%3DC%23D%3FE%2FF%2BG%25H%20proper%20form", url)
        assertEquals(1, url.count { it == '?' })
        assertTrue('#' !in url)
        assertTrue('&' !in url.substringAfter("search_query="))
    }

    @Test
    fun encodesNonAsciiNamesAsUtf8() {
        assertEquals(
            base + "Kn%C3%A4ubeuge%20proper%20form",
            ExerciseDemoLink.searchUrl("Knäubeuge")
        )
        assertEquals(
            base + "%E6%B7%B1%E8%B9%B2%20proper%20form",
            ExerciseDemoLink.searchUrl("深蹲")
        )
    }

    @Test
    fun keepsUnreservedCharactersReadable() {
        assertEquals(
            base + "Push-up_v2.0~x%20proper%20form",
            ExerciseDemoLink.searchUrl("Push-up_v2.0~x")
        )
    }
}
