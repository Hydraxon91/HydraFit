package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseNameSearchTest {

    @Test
    fun normalizesWhitespaceAndSeparators() {
        val normalized = normalizeExerciseSearchText("  Close-Grip   Bench-Press \n")
        assertEquals("Close Grip Bench Press", normalized)
        assertEquals("", normalizeExerciseSearchText("   "))
        assertEquals("", normalizeExerciseSearchText(" - "))
    }

    @Test
    fun keepsIgnoreCaseMatchingAfterSeparatorNormalization() {
        assertTrue(matchesExerciseNameQuery("Close-Grip Bench Press", "CLOSE GRIP BENCH"))
        assertTrue(matchesExerciseNameQuery("\u0130ncline Press", "incline press"))
    }

    @Test
    fun matchesAHyphenatedNameFromASpaceSeparatedQuery() {
        assertTrue(matchesExerciseNameQuery("Close-grip Bench Press", "close grip"))
        assertTrue(matchesExerciseNameQuery("Close-grip Bench Press", "CLOSE GRIP bench"))
    }

    @Test
    fun matchesASpaceSeparatedNameFromAHyphenatedQuery() {
        assertTrue(matchesExerciseNameQuery("Pull Up", "pull-up"))
    }

    @Test
    fun treatsUnicodeDashesAsSeparators() {
        assertTrue(matchesExerciseNameQuery("Close\u2010grip Bench Press", "close grip"))
        assertTrue(matchesExerciseNameQuery("Close\u2013grip Bench Press", "close grip"))
        assertTrue(matchesExerciseNameQuery("Close\u2014grip Bench Press", "close grip"))
        assertTrue(matchesExerciseNameQuery("Pull\u2015Up", "pull up"))
    }

    @Test
    fun keepsPartialSubstringMatching() {
        assertTrue(matchesExerciseNameQuery("Dumbbell Curl", "curl"))
        assertTrue(matchesExerciseNameQuery("Back Squat", "squ"))
    }

    @Test
    fun aBlankOrSeparatorOnlyQueryMatchesEverything() {
        assertTrue(matchesExerciseNameQuery("Back Squat", ""))
        assertTrue(matchesExerciseNameQuery("Back Squat", "   "))
        assertTrue(matchesExerciseNameQuery("Back Squat", "-"))
        assertTrue(matchesExerciseNameQuery("Back Squat", " \u2014 "))
    }

    @Test
    fun doesNotMatchUnrelatedNames() {
        assertFalse(matchesExerciseNameQuery("Back Squat", "bench"))
        assertFalse(matchesExerciseNameQuery("Close-grip Bench Press", "close squat"))
    }

    @Test
    fun keepsOtherPunctuationLiteral() {
        assertFalse(matchesExerciseNameQuery("Bench Press", "bench.press"))
    }
}
