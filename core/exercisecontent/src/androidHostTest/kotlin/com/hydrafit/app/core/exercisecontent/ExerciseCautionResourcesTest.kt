package com.hydrafit.app.core.exercisecontent

import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ExerciseCautionResourcesTest {

    @OptIn(ExperimentalResourceApi::class)
    @BeforeTest
    fun setUp() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }

    @Test
    fun allAuthoredCautionsResolveAndFallBackToEnglish() = runTest {
        val resources = ExerciseCautionSource.cautionByExerciseId.values.toList()
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val english = resources.map { getString(it) }
            english.forEachIndexed { index, text ->
                assertTrue(text.isNotBlank(), "${resources[index].key} resolves to blank content")
            }
            Locale.setDefault(Locale.GERMAN)
            assertEquals(english, resources.map { getString(it) })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun deadliftCautionsNameTheirMovement() = runTest {
        val conventional = requireNotNull(
            ExerciseCautionSource.cautionFor("conventional-deadlift")
        )
        val dumbbell = requireNotNull(ExerciseCautionSource.cautionFor("dumbbell-rdl"))
        assertTrue(getString(conventional).contains("bar"))
        assertTrue(getString(dumbbell).contains("dumbbell"))
    }
}
