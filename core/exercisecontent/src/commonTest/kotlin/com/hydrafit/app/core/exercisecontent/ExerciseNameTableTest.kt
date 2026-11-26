package com.hydrafit.app.core.exercisecontent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseNameTableTest {

    private val table = ExerciseNameTable(
        mapOf(
            "barbell-bench-press" to "Fekvenyomás rúddal",
            "lateral-raise" to "Oldalemelés",
            "barbell-row" to "Evezés rúddal"
        )
    )

    @Test
    fun anUntouchedBuiltInIsShownTranslated() {
        assertEquals(
            "Fekvenyomás rúddal",
            table.display("barbell-bench-press", "Barbell Bench Press")
        )
    }

    @Test
    fun aRenamedBuiltInACustomExerciseAndAnUnknownIdAreShownAsStored() {
        assertEquals("My Bench", table.display("barbell-bench-press", "My Bench"))
        assertEquals("Barbell Bench Press", table.display("custom-123", "Barbell Bench Press"))
        assertEquals("Sled Push", table.display("sled-push", "Sled Push"))
    }

    @Test
    fun aBuiltInWithoutATranslationFallsBackToItsStoredName() {
        assertEquals("Face Pull", table.display("face-pull", "Face Pull"))
    }

    @Test
    fun theStoredTableShowsEveryNameAsStored() {
        assertEquals(
            "Barbell Bench Press",
            ExerciseNameTable.Stored.display("barbell-bench-press", "Barbell Bench Press")
        )
    }

    @Test
    fun anUntouchedBuiltInMatchesItsShownAndItsEnglishName() {
        assertTrue(table.matches("barbell-bench-press", "Barbell Bench Press", "fekve"))
        assertTrue(table.matches("barbell-bench-press", "Barbell Bench Press", "bench"))
        assertFalse(table.matches("barbell-bench-press", "Barbell Bench Press", "squat"))
    }

    @Test
    fun aRenamedBuiltInMatchesOnlyItsOwnName() {
        assertTrue(table.matches("barbell-bench-press", "My Press", "press"))
        assertFalse(table.matches("barbell-bench-press", "My Press", "fekve"))
        assertFalse(table.matches("barbell-bench-press", "My Press", "barbell"))
    }

    @Test
    fun sortKeysPlaceAccentedLettersNextToTheirBaseLetters() {
        val names = listOf(
            "lateral-raise" to "Lateral Raise",
            "barbell-row" to "Barbell Row",
            "barbell-bench-press" to "Barbell Bench Press"
        )
        val sorted = names.sortedBy { (id, stored) -> table.sortKey(id, stored) }
            .map { (id, stored) -> table.display(id, stored) }
        assertEquals(listOf("Evezés rúddal", "Fekvenyomás rúddal", "Oldalemelés"), sorted)
    }

    @Test
    fun foldingMapsEveryAccentedLetterToOneBaseLetter() {
        assertEquals("evezes ruddal", foldForSort("Evezés Rúddal"))
        assertEquals("zluty kun ostrem", foldForSort("Žlutý kůň ostřem"))
        assertEquals("ollo uber", foldForSort("Öllő Über"))
    }

    @Test
    fun resourceKeysFollowTheExerciseId() {
        assertEquals("exercise_name_dumbbell_rdl", ExerciseNames.resourceKey("dumbbell-rdl"))
        assertTrue(ExerciseNames.isBuiltInName("dumbbell-rdl", "Dumbbell Romanian Deadlift"))
        assertFalse(ExerciseNames.isBuiltInName("dumbbell-rdl", "dumbbell romanian deadlift"))
    }
}
