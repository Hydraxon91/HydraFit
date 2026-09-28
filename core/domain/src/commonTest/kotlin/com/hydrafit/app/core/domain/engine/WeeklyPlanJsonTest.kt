package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WeeklyPlanJsonTest {

    @Test
    fun parsesPlainJson() {
        val plan = parseWeeklyPlan(PLAIN, PlannerEngineId.GEMINI_API)

        assertEquals(1, plan.days.size)
        assertEquals(SplitFocus.PUSH, plan.days.single().focus)
        assertEquals("bench-press", plan.days.single().exercises.single().exerciseId)
    }

    @Test
    fun parsesJsonWrappedInAMarkdownFence() {
        val fenced = "```json\n$PLAIN\n```"

        val plan = parseWeeklyPlan(fenced, PlannerEngineId.LOCAL_LLM)

        assertEquals(1, plan.days.size)
        assertEquals("bench-press", plan.days.single().exercises.single().exerciseId)
    }

    @Test
    fun parsesJsonSurroundedByProse() {
        val noisy = "Sure, here is your plan:\n$PLAIN\nLet me know if you want changes."

        val plan = parseWeeklyPlan(noisy, PlannerEngineId.LOCAL_LLM)

        assertEquals(1, plan.days.size)
    }

    @Test
    fun defaultsUnrecognizedFocusToFullBody() {
        val plan = parseWeeklyPlan(
            """{"days":[{"focus":"PUSH|PULL|LEGS","exercises":[]}]}""",
            PlannerEngineId.LOCAL_LLM
        )

        assertEquals(SplitFocus.FULL_BODY, plan.days.single().focus)
    }

    @Test
    fun clampsOutOfRangeSetsAndReps() {
        val plan = parseWeeklyPlan(
            """{"days":[{"focus":"PUSH","exercises":[""" +
                """{"exerciseId":"bench-press","sets":0,"reps":999}]}]}""",
            PlannerEngineId.LOCAL_LLM
        )

        val planned = plan.days.single().exercises.single()
        assertEquals(1, planned.sets)
        assertEquals(100, planned.reps)
    }

    @Test
    fun rejectsTextWithoutAJsonObject() {
        assertFailsWith<IllegalArgumentException> {
            parseWeeklyPlan("I am not JSON", PlannerEngineId.LOCAL_LLM)
        }
    }

    private companion object {
        const val PLAIN =
            """{"days":[{"focus":"PUSH","exercises":[{"exerciseId":"bench-press","sets":3,"reps":8}]}]}"""
    }
}
