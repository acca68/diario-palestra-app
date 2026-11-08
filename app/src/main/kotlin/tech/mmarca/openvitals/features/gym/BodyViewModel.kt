package tech.mmarca.openvitals.features.gym

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.mmarca.openvitals.data.repository.contract.BodyRepository
import tech.mmarca.openvitals.domain.model.BodyMeasurementType
import tech.mmarca.openvitals.domain.model.BodyMeasurementWriteRequest
import tech.mmarca.openvitals.domain.model.accepts

enum class BodyMessage { SAVED, SAVED_APP_ONLY, DELETED, IMPORTED, IMPORT_FAILED }

@Immutable
data class BodyUiState(
    val isLoading: Boolean = true,
    val reports: List<BodyReport> = emptyList(),
    val mapGroup: BodyFieldGroup = BodyFieldGroup.CIRCUMFERENCE,
    val trendField: BodyField = BodyField.WEIGHT,
    val editing: BodyReport? = null,
    val isEditorOpen: Boolean = false,
    val message: BodyMessage? = null,
    val importedCount: Int = 0,
) {
    val latest: BodyReport? get() = reports.maxByOrNull { it.date }
}

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val store: BodyReportStore,
    private val bodyRepository: BodyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodyUiState())
    val uiState: StateFlow<BodyUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val reports = store.load().sortedByDescending { it.date }
            _uiState.update { it.copy(isLoading = false, reports = reports) }
        }
    }

    fun showMap(group: BodyFieldGroup) = _uiState.update { it.copy(mapGroup = group) }

    fun showTrend(field: BodyField) = _uiState.update { it.copy(trendField = field) }

    fun newReport() = _uiState.update { it.copy(isEditorOpen = true, editing = null) }

    fun edit(report: BodyReport) = _uiState.update { it.copy(isEditorOpen = true, editing = report) }

    fun closeEditor() = _uiState.update { it.copy(isEditorOpen = false, editing = null) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun save(report: BodyReport) {
        viewModelScope.launch {
            val previous = _uiState.value.editing
            // A new date means a new report: the old one and its Health Connect records go.
            if (previous != null && previous.date != report.date) deleteEverywhere(previous)
            val ids = runCatching { syncHealthConnect(report, previous?.takeIf { it.date == report.date }) }
            store.save(report.copy(healthConnectIds = ids.getOrDefault(previous?.healthConnectIds.orEmpty())))
            _uiState.update {
                it.copy(
                    isEditorOpen = false,
                    editing = null,
                    message = if (ids.isSuccess) BodyMessage.SAVED else BodyMessage.SAVED_APP_ONLY,
                )
            }
            reload()
        }
    }

    fun delete(report: BodyReport) {
        viewModelScope.launch {
            deleteEverywhere(report)
            _uiState.update { it.copy(isEditorOpen = false, editing = null, message = BodyMessage.DELETED) }
            reload()
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val reports = runCatching { store.readImport(uri) }.getOrElse {
                _uiState.update { state -> state.copy(message = BodyMessage.IMPORT_FAILED) }
                return@launch
            }
            val existing = store.load().associateBy { it.date }
            reports.forEach { report ->
                val old = existing[report.date]
                val ids = runCatching { syncHealthConnect(report, old) }.getOrDefault(old?.healthConnectIds.orEmpty())
                store.save(report.copy(healthConnectIds = ids))
            }
            _uiState.update { it.copy(message = BodyMessage.IMPORTED, importedCount = reports.size) }
            reload()
        }
    }

    private suspend fun deleteEverywhere(report: BodyReport) {
        report.healthConnectIds.forEach { (type, id) ->
            runCatching { bodyRepository.deleteBodyMeasurementEntry(type, id) }
        }
        store.delete(report.date)
    }

    /**
     * Writes the values Health Connect knows (weight, body fat, lean mass, water, bone, BMR),
     * updating the records of [previous] when it has them, so nothing is duplicated.
     */
    private suspend fun syncHealthConnect(report: BodyReport, previous: BodyReport?): Map<BodyMeasurementType, String> {
        val time = report.date
            .atTime(report.time ?: LocalTime.of(8, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val ids = previous?.healthConnectIds.orEmpty().toMutableMap()
        val toWrite = mutableListOf<BodyMeasurementWriteRequest>()
        BodyField.entries.forEach { field ->
            val type = field.healthConnect ?: return@forEach
            val value = report[field]
            val existingId = ids[type]
            when {
                value == null || !type.accepts(value) -> {
                    if (existingId != null) {
                        bodyRepository.deleteBodyMeasurementEntry(type, existingId)
                        ids.remove(type)
                    }
                }
                existingId != null ->
                    bodyRepository.updateBodyMeasurementEntry(existingId, BodyMeasurementWriteRequest(type, time, value))
                else -> toWrite += BodyMeasurementWriteRequest(type, time, value)
            }
        }
        if (toWrite.isNotEmpty()) {
            val written = bodyRepository.writeBodyMeasurementEntries(toWrite)
            toWrite.zip(written).forEach { (request, id) -> ids[request.type] = id }
        }
        return ids
    }
}
