package com.hydrafit.app.core.userdata.transfer

import com.hydrafit.app.core.domain.transfer.PrescriptionLineWrapper
import com.hydrafit.app.core.domain.transfer.PrescriptionPage

/**
 * Renders paginated prescription lines to a PDF byte array. Android draws real pages with
 * `android.graphics.pdf.PdfDocument`; platforms without a renderer report [isSupported] false and
 * throw from [render]. The caller decides when to render and where to write the bytes; the suggested
 * document filename belongs to the document picker, not the renderer.
 *
 * A renderer is also the [PrescriptionLineWrapper] the layout uses, because only it knows the real
 * font metrics: it wraps each logical line to the page's text width before pagination, so a long
 * exercise name cannot clip its sets, reps or weight.
 */
interface PrescriptionPdfRenderer : PrescriptionLineWrapper {
    val isSupported: Boolean

    fun render(pages: List<PrescriptionPage>): ByteArray
}

/** Fallback binding for platforms with no PDF renderer (iOS in 0.9.x). */
class UnsupportedPrescriptionPdfRenderer : PrescriptionPdfRenderer {
    override val isSupported: Boolean = false

    /** Never reached on an unsupported platform, so it keeps the text unchanged. */
    override fun wrap(text: String): List<String> = listOf(text)

    override fun render(pages: List<PrescriptionPage>): ByteArray =
        throw UnsupportedOperationException("PDF export is not supported on this platform")
}
