package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.WarmupAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The tag builders are the only source of the Logger's external identities, so a distinct control must
 * always map to a distinct tag and the same control must always map to the same tag. The tags depend on
 * stable identity alone (exercise id, warm-up action, occurrence entry and ramp index) and never on a
 * displayed value, so an edit or a state change cannot move a target.
 */
class LoggerTestTagsTest {

    @Test
    fun preparationTogglesAreDistinctPerAction() {
        val actions = WarmupAction.entries
        val tags = actions.map { LoggerTestTags.preparationToggle(it) }
        assertEquals(actions.size, tags.toSet().size)
        assertTrue(tags.all { it.startsWith("logger_warmup_prep_") })
    }

    @Test
    fun theThreePreparationControlsOfOneActionAreDistinct() {
        WarmupAction.entries.forEach { action ->
            val controls = setOf(
                LoggerTestTags.preparationToggle(action),
                LoggerTestTags.preparationEdit(action),
                LoggerTestTags.preparationSkip(action)
            )
            assertEquals(3, controls.size)
        }
    }

    @Test
    fun preparationTagsAreStableForTheSameAction() {
        assertEquals(
            LoggerTestTags.preparationToggle(WarmupAction.LIGHT_PUSH_PRACTICE),
            LoggerTestTags.preparationToggle(WarmupAction.LIGHT_PUSH_PRACTICE)
        )
    }

    @Test
    fun exerciseTagsCarryTheStableIdAndStayDistinctAcrossSharedPrefixes() {
        assertEquals("logger_exercise_bench-press", LoggerTestTags.exercise("bench-press"))
        assertNotEquals(LoggerTestTags.exercise("bench"), LoggerTestTags.exercise("bench-press"))
    }

    @Test
    fun rampRowTagsAreDistinctPerEntryAndIndex() {
        assertEquals("logger_warmup_ramp_10_2", LoggerTestTags.rampRow(10L, 2))
        // A naive string join would let these two identities collide; the separator keeps them apart.
        assertNotEquals(LoggerTestTags.rampRow(1L, 23), LoggerTestTags.rampRow(12L, 3))
    }

    @Test
    fun theThreeRampControlsOfOneRowAreDistinct() {
        val controls = setOf(
            LoggerTestTags.rampRow(10L, 2),
            LoggerTestTags.rampEdit(10L, 2),
            LoggerTestTags.rampLog(10L, 2)
        )
        assertEquals(3, controls.size)
    }

    @Test
    fun staticAndEditorTagsAreDistinctFromEachOther() {
        val tags = listOf(
            LoggerTestTags.LIST,
            LoggerTestTags.EXERCISE_PICKER,
            LoggerTestTags.WARMUP_CARD,
            LoggerTestTags.WARMUP_EDITOR,
            LoggerTestTags.WARMUP_EDITOR_DURATION,
            LoggerTestTags.WARMUP_EDITOR_REPS,
            LoggerTestTags.WARMUP_EDITOR_WEIGHT,
            LoggerTestTags.WARMUP_EDITOR_SAVE,
            LoggerTestTags.WARMUP_EDITOR_CANCEL
        )
        assertEquals(tags.size, tags.toSet().size)
    }
}
