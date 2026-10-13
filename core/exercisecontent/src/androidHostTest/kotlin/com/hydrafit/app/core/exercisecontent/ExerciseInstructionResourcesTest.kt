package com.hydrafit.app.core.exercisecontent

import hydrafit.core.exercisecontent.generated.resources.Res
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_close
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_execution
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_setup
import hydrafit.core.exercisecontent.generated.resources.exercise_instruction_unavailable
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class ExerciseInstructionResourcesTest {

    @OptIn(ExperimentalResourceApi::class)
    @BeforeTest
    fun setUp() {
        setResourceReaderAndroidContext(RuntimeEnvironment.getApplication())
    }

    @Test
    fun allAuthoredSectionsAndLabelsResolveAndFallBackToEnglish() = runTest {
        val resources = ExerciseInstructionSource.keysByExerciseId.values
            .flatMap { listOf(it.setup, it.execution) } + listOf(
            Res.string.exercise_instruction_setup,
            Res.string.exercise_instruction_execution,
            Res.string.exercise_instruction_close,
            Res.string.exercise_instruction_unavailable
        )
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
    fun calfInstructionsDescribeTheirSeparateEquipment() = runTest {
        val dumbbell = requireNotNull(ExerciseInstructionSource.keysFor("standing-calf-raise"))
        val machine = requireNotNull(
            ExerciseInstructionSource.keysFor("standing-calf-raise-machine")
        )
        val dumbbellSetup = getString(dumbbell.setup)
        val dumbbellExecution = getString(dumbbell.execution)
        assertTrue(dumbbellSetup.contains("dumbbell"))
        assertTrue(dumbbellExecution.contains("dumbbell"))
        assertFalse(dumbbellSetup.contains("machine"))
        assertTrue(getString(machine.setup).contains("machine"))
        assertTrue(getString(machine.setup).contains("platform"))
        assertTrue(getString(machine.execution).contains("safety catch"))
    }
}
