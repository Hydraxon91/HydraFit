package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvolvementEditorStateTest {

    private fun draft(weight: Double?) = InvolvementEditorState.forMuscle(MuscleGroup.QUADS, weight)

    @Test
    fun anUnsetMuscleSeedsZeroAndItsUntouchedApplyIsANoOp() {
        val unset = draft(null)
        assertEquals(InvolvementEditorState.NONE_INPUT, unset.input)
        assertEquals(0.0, unset.enteredWeight)
        assertTrue(unset.isValid)
        assertNull(unset.weightToApply())
    }

    @Test
    fun blankInputIsInvalidNeverASilentRemoval() {
        val blank = draft(0.5).copy(input = "")
        assertFalse(blank.isValid)
        assertNull(blank.weightToApply())

        val whitespace = draft(0.5).copy(input = "   ")
        assertFalse(whitespace.isValid)
        assertNull(whitespace.weightToApply())
    }

    @Test
    fun explicitZeroOrNoneRemovesTheContribution() {
        val zero = draft(0.5).copy(input = "0")
        assertTrue(zero.isValid)
        assertNull(zero.weightToApply())

        val alsoZero = draft(null).copy(input = "0.0")
        assertTrue(alsoZero.isValid)
        assertNull(alsoZero.weightToApply())
    }

    @Test
    fun onlyFiniteValuesWithinZeroToOneAreValid() {
        listOf("0", "0.0", "1", "1.0", "0.5", "0.83").forEach { text ->
            assertTrue(draft(0.5).copy(input = text).isValid, "expected valid: $text")
        }
        listOf("1.0001", "1.5", "-0.1", "2").forEach { text ->
            assertFalse(draft(0.5).copy(input = text).isValid, "expected out of range: $text")
        }
    }

    @Test
    fun nonFiniteAndMalformedInputIsRejected() {
        listOf("NaN", "Infinity", "-Infinity", "abc", "0.5.5", "1,5", "--", "0x1").forEach { text ->
            val state = draft(0.5).copy(input = text)
            assertFalse(state.isValid, "expected invalid: $text")
            assertNull(state.weightToApply(), "invalid input must not resolve a weight: $text")
        }
    }

    @Test
    fun tierShortcutsAreValidAndWritable() {
        INVOLVEMENT_TIERS.forEach { tier ->
            val state = draft(null).copy(input = tier.toString())
            assertTrue(state.isValid)
            assertEquals(tier, state.weightToApply())
        }
    }

    @Test
    fun tierDiscriminationKeepsNonTierValuesDistinct() {
        assertEquals(InvolvementTier.LOW, involvementTierOf(0.3))
        assertEquals(InvolvementTier.MID, involvementTierOf(0.5))
        assertEquals(InvolvementTier.HIGH, involvementTierOf(0.7))
        assertEquals(InvolvementTier.PRIMARY, involvementTierOf(1.0))
        assertNull(involvementTierOf(0.83))
        assertNull(involvementTierOf(0.0))
    }

    @Test
    fun theChipCycleWalksTheTiersThenClears() {
        assertNull(nextInvolvementTier(1.0))
        assertNull(nextInvolvementTier(0.83))
        assertEquals(0.3, nextInvolvementTier(null))
        assertEquals(0.5, nextInvolvementTier(0.3))
        assertEquals(0.7, nextInvolvementTier(0.5))
        assertEquals(1.0, nextInvolvementTier(0.7))
    }

    @Test
    fun anUntouchedFieldKeepsItsExactStoredDouble() {
        // 0.1 + 0.2 is the classic value whose decimal form is not exact; the origin must
        // round-trip without a text approximation.
        val tricky = 0.1 + 0.2
        listOf(0.83, 0.30000000000000004, tricky, 1.0).forEach { stored ->
            val state = draft(stored)
            assertTrue(state.isValid)
            assertEquals(stored, state.weightToApply())
        }
    }

    @Test
    fun changedTextUsesTheParsedValueNotTheOrigin() {
        val state = draft(0.5).copy(input = "0.85")
        assertTrue(state.isValid)
        assertEquals(0.85, state.weightToApply())
    }
}
