package com.hydrafit.app.core.userdata.transfer

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.hydrafit.app.core.domain.transfer.PrescriptionLineRole
import com.hydrafit.app.core.domain.transfer.PrescriptionPage
import java.io.ByteArrayOutputStream

/**
 * Draws the paginated prescription onto A4-ish pages with [android.graphics.pdf.PdfDocument].
 *
 * It is also the layout's line wrapper: [wrap] measures real text at the body size against the page's
 * text width and never drops content, so an over-long exercise name or a long unbroken token is
 * broken across lines instead of being clipped. [render] additionally starts a new PDF page if a
 * wrapped line would run past the bottom margin, so no page can overflow even if a caller supplies an
 * optimistic line count.
 */
class AndroidPrescriptionPdfRenderer : PrescriptionPdfRenderer {
    override val isSupported: Boolean = true

    override fun wrap(text: String, role: PrescriptionLineRole): List<String> {
        val measure = Paint().apply {
            isAntiAlias = true
            textSize = textSizeFor(role)
        }
        val available = PAGE_WIDTH - 2 * MARGIN
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines += ""
            } else {
                lines += wrapParagraph(measure, paragraph, available)
            }
        }
        // A wrapper must never return nothing for a non-empty line; an empty line stays one line.
        return lines.ifEmpty { listOf(text) }
    }

    override fun render(pages: List<PrescriptionPage>): ByteArray {
        val document = PdfDocument()
        try {
            val paint = Paint().apply { isAntiAlias = true }
            var pageNumber = 1
            var pdfPage = document.startPage(pageInfo(pageNumber))
            var y = MARGIN
            pages.flatMap { it.lines }.forEach { line ->
                if (y + LINE_SPACING > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(pdfPage)
                    pageNumber++
                    pdfPage = document.startPage(pageInfo(pageNumber))
                    y = MARGIN
                }
                paint.textSize = textSizeFor(line.role)
                paint.isFakeBoldText = line.role != PrescriptionLineRole.BODY
                pdfPage.canvas.drawText(line.text, MARGIN, y, paint)
                y += LINE_SPACING
            }
            document.finishPage(pdfPage)
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun pageInfo(pageNumber: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    /**
     * Greedy word wrap: accumulate words while they fit, then break. A single word wider than the
     * page is hard-broken with `breakText`, so content is laid out rather than clipped.
     */
    private fun wrapParagraph(measure: Paint, paragraph: String, available: Float): List<String> {
        if (measure.measureText(paragraph) <= available) return listOf(paragraph)
        val lines = mutableListOf<String>()
        var start = 0
        while (start < paragraph.length) {
            val remaining = paragraph.substring(start)
            if (measure.measureText(remaining) <= available) {
                lines += remaining
                break
            }
            var fit = measure.breakText(remaining, true, available, null)
            if (fit <= 0) fit = 1
            // Prefer the last space inside the fitting prefix; otherwise hard-break the token.
            val space = remaining.lastIndexOf(' ', fit - 1)
            val take = if (space > 0) space else fit
            lines += remaining.substring(0, take)
            start += take
            if (start < paragraph.length && paragraph[start] == ' ') start++
        }
        return lines.ifEmpty { listOf(paragraph) }
    }

    /** The type size each role is drawn at; wrapping must use the same one or a line can overrun. */
    private fun textSizeFor(role: PrescriptionLineRole): Float = when (role) {
        PrescriptionLineRole.TITLE -> TITLE_TEXT_SIZE
        PrescriptionLineRole.HEADING -> HEADING_TEXT_SIZE
        PrescriptionLineRole.BODY -> BODY_TEXT_SIZE
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val LINE_SPACING = 16f

        // The layout gives each line a role; the role decides the drawn type size and weight.
        const val TITLE_TEXT_SIZE = 18f
        const val HEADING_TEXT_SIZE = 14f
        const val BODY_TEXT_SIZE = 11f
    }
}
