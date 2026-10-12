package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Representative coverage for the generated Unicode 17.0.0 fold table: ASCII, Latin-1, Latin
 * Extended, Greek (including the final-sigma and iota-subscript exceptions), Greek-derived symbols,
 * fullwidth, Roman numerals, the multi-code-point exceptions, and supplementary-plane scripts.
 */
class UnicodeCaseFoldTest {

    @Test
    fun foldsAsciiCase() {
        assertEquals("back squat", unicodeCaseFold("Back Squat"))
    }

    @Test
    fun foldsAccentedLatinWithoutStrippingAccents() {
        assertEquals("\u00E9paules", unicodeCaseFold("\u00C9paules"))
        assertEquals(unicodeCaseFold("\u00C9paules"), unicodeCaseFold("\u00C9PAULES"))
        // Accent-sensitive: the unaccented form folds to a different string.
        assertNotEquals(unicodeCaseFold("\u00C9paules"), unicodeCaseFold("Epaules"))
    }

    @Test
    fun appliesFullFoldsThatExpandToMultipleCodePoints() {
        assertEquals("strasse", unicodeCaseFold("Stra\u00DFe"))
        assertEquals("ss", unicodeCaseFold("\u1E9E"))
        assertEquals("i\u0307", unicodeCaseFold("\u0130"))
        assertEquals("fi fl ff", unicodeCaseFold("\uFB01 \uFB02 \uFB00"))
        assertEquals("\u03B9\u0308\u0301", unicodeCaseFold("\u0390"))
    }

    @Test
    fun foldsGreekFinalSigmaAndIotaSubscript() {
        assertEquals("\u03C3", unicodeCaseFold("\u03C2"))
        assertEquals("\u03C3", unicodeCaseFold("\u03A3"))
        assertEquals("\u03B9", unicodeCaseFold("\u0345"))
        assertEquals("\u03BC", unicodeCaseFold("\u00B5"))
        assertEquals("\u1E61", unicodeCaseFold("\u1E9B"))
    }

    @Test
    fun foldsCompatibilityAndLetterlikeSymbols() {
        assertEquals("k", unicodeCaseFold("\u212A"))
        assertEquals("\u03C9", unicodeCaseFold("\u2126"))
        assertEquals("\uFF41", unicodeCaseFold("\uFF21"))
        assertEquals("\u2170", unicodeCaseFold("\u2160"))
        assertEquals("\u24D0", unicodeCaseFold("\u24B6"))
    }

    @Test
    fun foldsSupplementaryPlaneCodePoints() {
        assertEquals("\uD801\uDC28", unicodeCaseFold("\uD801\uDC00"))
        assertEquals("\u13A0", unicodeCaseFold("\uAB70"))
        assertEquals("\uD83A\uDD22", unicodeCaseFold("\uD83A\uDD00"))
    }

    /** [foldDelta] relies on the range table being sorted and disjoint for its early return. */
    @Test
    fun foldRangesAreSortedDisjointAndWellFormed() {
        var previousEnd = -1
        FOLD_RANGES.forEach { range ->
            assertTrue(range.start <= range.end, "start must not exceed end: $range")
            assertTrue(range.start > previousEnd, "ranges must be sorted and disjoint: $range")
            assertTrue(range.step >= 1, "step must be positive: $range")
            assertTrue(
                (range.end - range.start) % range.step == 0,
                "end must align to step: $range"
            )
            previousEnd = range.end
        }
    }

    /**
     * The generated 17.0.0 table has 1,585 mappings: 1,481 covered by delta ranges and 104
     * multi-code-point exceptions. Any change to this contract must re-run
     * `scripts/check-case-fold-table.py` against the published `CaseFolding.txt`.
     */
    @Test
    fun theGeneratedTableKeepsItsDocumentedShape() {
        val covered = FOLD_RANGES.sumOf { (it.end - it.start) / it.step + 1 }
        assertEquals(199, FOLD_RANGES.size)
        assertEquals(1_481, covered)
        assertEquals(104, FOLD_EXCEPTIONS.size)
        assertEquals(1_585, covered + FOLD_EXCEPTIONS.size)
    }

    /** No code point may appear in both tables: the delta ranges and the exceptions are disjoint. */
    @Test
    fun foldExceptionsDoNotOverlapTheDeltaRanges() {
        val covered = buildSet {
            FOLD_RANGES.forEach { range ->
                var codePoint = range.start
                while (codePoint <= range.end) {
                    add(codePoint)
                    codePoint += range.step
                }
            }
        }
        val overlapping = FOLD_EXCEPTIONS.keys.filter { it in covered }
        assertTrue(overlapping.isEmpty(), "exceptions inside a delta range: $overlapping")
    }

    /** Every exception target is already fully folded, so folding is idempotent entry by entry. */
    @Test
    fun everyFoldExceptionTargetIsAlreadyFolded() {
        FOLD_EXCEPTIONS.forEach { (source, targets) ->
            val expected = stringOf(targets)
            assertEquals(
                expected,
                unicodeCaseFold(expected),
                "exception target for U+${source.toString(16)} is not fold-closed"
            )
        }
    }

    /** Builds a string from code points without relying on a platform-only API. */
    private fun stringOf(codePoints: List<Int>): String = buildString {
        codePoints.forEach { codePoint ->
            if (codePoint <= 0xFFFF) {
                append(codePoint.toChar())
            } else {
                val scalar = codePoint - 0x10000
                append((0xD800 + (scalar shr 10)).toChar())
                append((0xDC00 + (scalar and 0x3FF)).toChar())
            }
        }
    }

    @Test
    fun leavesUncasedTextUnchanged() {
        assertEquals("2026-10-12 12:00", unicodeCaseFold("2026-10-12 12:00"))
        assertEquals("\uD83D\uDCAA\uD83C\uDFCB", unicodeCaseFold("\uD83D\uDCAA\uD83C\uDFCB"))
        assertEquals("", unicodeCaseFold(""))
    }
}
