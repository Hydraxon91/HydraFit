package com.hydrafit.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class OpenSourceLicensesTest {

    private fun validText() = buildString {
        append("HydraFit third-party notices\n\n")
        append("2. Apache License 2.0\n")
        append("Public Suffix List exact source\n")
        repeat(600) { append("line $it: permission notice text\n") }
    }

    @Test
    fun blocksRoundTripAndStayBounded() {
        val text = buildString {
            repeat(900) { append("Paragraph $it: third-party notice text.\n") }
        }
        val blocks = licenseTextBlocks(text, maxChars = 500)
        assertTrue(blocks.isNotEmpty())
        assertTrue(blocks.all { it.length <= 500 })
        assertTrue(blocks.all { it.isNotEmpty() })
        assertEquals(text, blocks.joinToString(""))
    }

    @Test
    fun blockBoundariesPreferNewlinesButHardSplitLongLines() {
        val text = "a".repeat(250) + "\n" + "b".repeat(1_200)
        val blocks = licenseTextBlocks(text, maxChars = 400)
        assertEquals(text, blocks.joinToString(""))
        assertTrue(blocks.first().endsWith("\n"))
        assertTrue(blocks.all { it.length <= 400 })
    }

    @Test
    fun validBundledTextLoadsFromThePackagedPath() = runTest {
        var requested: String? = null
        val result = loadLicenses { path ->
            requested = path
            validText().encodeToByteArray()
        }
        assertEquals(LICENSES_ASSET_PATH, requested)
        assertTrue(result.isSuccess)
        assertEquals(validText(), result.getOrThrow())
    }

    @Test
    fun emptyOrIncompleteTextIsAFailureNotAnEmptySuccess() = runTest {
        listOf(
            "",
            "HydraFit third-party notices",
            "Apache License\n".repeat(1_000)
        ).forEach { bad ->
            val result = loadLicenses { bad.encodeToByteArray() }
            assertTrue(result.isFailure, "expected failure for ${bad.take(20)}")
        }
    }

    @Test
    fun readFailureIsReportedAndRetryCanSucceed() = runTest {
        var attempts = 0
        suspend fun read(path: String): ByteArray {
            attempts++
            if (attempts == 1) error("asset read failed")
            return validText().encodeToByteArray()
        }
        assertTrue(loadLicenses(::read).isFailure)
        val retried = loadLicenses(::read)
        assertTrue(retried.isSuccess)
        assertEquals(2, attempts)
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingARetryableFailure() = runTest {
        assertFailsWith<CancellationException> {
            loadLicenses { throw CancellationException("left the licenses page") }
        }
    }

    @Test
    fun readyStateKeepsEveryBlock() {
        val text = validText()
        val state = assertIs<LicensesUiState.Ready>(
            LicensesUiState.Ready(licenseTextBlocks(text))
        )
        assertEquals(text, state.blocks.joinToString(""))
        assertEquals(state.blocks.size, state.blocks.distinct().size)
    }
}
