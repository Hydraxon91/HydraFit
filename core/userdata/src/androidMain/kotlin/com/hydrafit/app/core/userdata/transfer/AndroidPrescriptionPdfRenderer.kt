package com.hydrafit.app.core.userdata.transfer

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.hydrafit.app.core.domain.transfer.PrescriptionLineRole
import com.hydrafit.app.core.domain.transfer.PrescriptionPage
import java.io.ByteArrayOutputStream

/** Draws the paginated prescription onto A4-ish pages with [android.graphics.pdf.PdfDocument]. */
class AndroidPrescriptionPdfRenderer : PrescriptionPdfRenderer {
    override val isSupported: Boolean = true

    override fun render(pages: List<PrescriptionPage>): ByteArray {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo
                    .Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1)
                    .create()
                val pdfPage = document.startPage(info)
                val paint = Paint().apply { isAntiAlias = true }
                var y = MARGIN
                page.lines.forEach { line ->
                    when (line.role) {
                        PrescriptionLineRole.TITLE -> paint.textSize = TITLE_TEXT_SIZE
                        PrescriptionLineRole.HEADING -> paint.textSize = HEADING_TEXT_SIZE
                        PrescriptionLineRole.BODY -> paint.textSize = BODY_TEXT_SIZE
                    }
                    paint.isFakeBoldText = line.role != PrescriptionLineRole.BODY
                    pdfPage.canvas.drawText(line.text, MARGIN, y, paint)
                    y += LINE_SPACING
                }
                document.finishPage(pdfPage)
            }
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
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
