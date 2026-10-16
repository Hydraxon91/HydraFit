package com.hydrafit.app.feature.splitbuilder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.ExportPrescriptionPdfUseCase
import com.hydrafit.app.core.domain.transfer.ExportPrescriptionsUseCase
import com.hydrafit.app.core.domain.transfer.PrescriptionException
import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import com.hydrafit.app.core.domain.transfer.PrescriptionJson
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import com.hydrafit.app.core.userdata.transfer.PrescriptionDocumentStore
import com.hydrafit.app.core.userdata.transfer.PrescriptionPdfRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why an exported plan could not be written; the UI maps each to a localized message. */
enum class PlanExportFailure {
    TOO_LARGE,
    OVER_LIMIT,
    IO
}

sealed interface PlanExportStatus {
    data object Saved : PlanExportStatus

    data object Printed : PlanExportStatus

    data class Failed(val failure: PlanExportFailure) : PlanExportStatus
}

data class PlanExportUiState(
    /** The explicit recorded choice; prescribed weights are omitted until it is turned on. */
    val includeWeights: Boolean = false,
    val inProgress: Boolean = false,
    /** False when this platform has no JSON document surface, so the screen shows a note instead. */
    val jsonSupported: Boolean = true,
    /** False when the renderer or the document writer cannot produce a PDF on this platform. */
    val pdfSupported: Boolean = true,
    val status: PlanExportStatus? = null
)

/** Stable identities for the plan-transfer controls; technical metadata, never spoken labels. */
object PlanExportTestTags {
    const val INCLUDE_WEIGHTS = "plan_export_weights"
    const val EXPORT_JSON = "plan_export_json"
    const val PRINT_PDF = "plan_export_pdf"
}

/**
 * Exports the plan currently on screen — the displayed draft or the accepted week — without saving
 * it. The plan is converted to an in-memory routine draft and handed to the in-memory export paths,
 * so exporting or printing never creates, saves or modifies a routine. A PDF is only rendered when
 * both the renderer and the document writer support it. Cancellation is rethrown.
 */
class PlanExportViewModel(
    private val convertPlanToTemplate: ConvertPlanToTemplateUseCase,
    private val exportPrescriptions: ExportPrescriptionsUseCase,
    private val exportPdf: ExportPrescriptionPdfUseCase,
    private val pdfRenderer: PrescriptionPdfRenderer,
    private val documentStore: PrescriptionDocumentStore,
    private val fileStore: BackupFileStore,
    private val appVersionProvider: AppVersionProvider,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlanExportUiState(
            jsonSupported = fileStore.isSupported,
            pdfSupported = pdfRenderer.isSupported && documentStore.isSupported
        )
    )
    val state: StateFlow<PlanExportUiState> = _state.asStateFlow()

    fun onIncludeWeightsChanged(includeWeights: Boolean) {
        _state.update { it.copy(includeWeights = includeWeights) }
    }

    fun onStatusDismissed() {
        _state.update { it.copy(status = null) }
    }

    /** Writes the displayed plan as a routine interchange file to the chosen document. */
    fun exportJson(plan: WeeklyPlan, name: String, handle: String) {
        if (!_state.value.jsonSupported || _state.value.inProgress) return
        val includeWeights = _state.value.includeWeights
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val prescriptions = exportPrescriptions.document(
                    templates = listOf(convertPlanToTemplate(plan, name)),
                    includeWeights = includeWeights,
                    appVersion = appVersionProvider.versionName,
                    exportedAtMillis = timeProvider.nowMillis()
                )
                fileStore.write(handle, PrescriptionJson.encode(prescriptions))
                _state.update { it.copy(inProgress = false, status = PlanExportStatus.Saved) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        inProgress = false,
                        status = PlanExportStatus.Failed(throwable.toPlanExportFailure())
                    )
                }
            }
        }
    }

    /** Renders and writes the displayed plan as a PDF to the chosen document. */
    fun printPdf(plan: WeeklyPlan, name: String, handle: String) {
        if (!_state.value.pdfSupported || _state.value.inProgress) return
        val includeWeights = _state.value.includeWeights
        _state.update { it.copy(inProgress = true, status = null) }
        viewModelScope.launch {
            try {
                val pages = exportPdf.pages(
                    templates = listOf(convertPlanToTemplate(plan, name)),
                    includeWeights = includeWeights,
                    appVersion = appVersionProvider.versionName,
                    exportedAtMillis = timeProvider.nowMillis(),
                    linesPerPage = PLAN_PDF_LINES_PER_PAGE
                )
                documentStore.write(handle, pdfRenderer.render(pages))
                _state.update { it.copy(inProgress = false, status = PlanExportStatus.Printed) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        inProgress = false,
                        status = PlanExportStatus.Failed(throwable.toPlanExportFailure())
                    )
                }
            }
        }
    }

    private fun Throwable.toPlanExportFailure(): PlanExportFailure = when {
        this is PrescriptionException && failure == PrescriptionFailure.TOO_LARGE ->
            PlanExportFailure.TOO_LARGE

        this is PrescriptionException && failure == PrescriptionFailure.OVER_LIMIT ->
            PlanExportFailure.OVER_LIMIT

        this is BackupException && failure == BackupFailure.TOO_LARGE ->
            PlanExportFailure.TOO_LARGE

        else -> PlanExportFailure.IO
    }

    companion object {
        /** Layout lines one A4 page holds at the renderer's type sizes; the layout paginates. */
        const val PLAN_PDF_LINES_PER_PAGE: Int = 44
    }
}
