package com.hydrafit.app.core.userdata.transfer

import com.hydrafit.app.core.domain.transfer.PrescriptionPage

/**
 * Renders paginated prescription lines to a PDF byte array. Android draws real pages with
 * `android.graphics.pdf.PdfDocument`; platforms without a renderer report [isSupported] false and
 * throw from [render]. The caller decides when to render and where to write the bytes; the suggested
 * document filename belongs to the document picker, not the renderer.
 */
interface PrescriptionPdfRenderer {
    val isSupported: Boolean

    fun render(pages: List<PrescriptionPage>): ByteArray
}

/** Fallback binding for platforms with no PDF renderer (iOS in 0.9.x). */
class UnsupportedPrescriptionPdfRenderer : PrescriptionPdfRenderer {
    override val isSupported: Boolean = false

    override fun render(pages: List<PrescriptionPage>): ByteArray =
        throw UnsupportedOperationException("PDF export is not supported on this platform")
}
