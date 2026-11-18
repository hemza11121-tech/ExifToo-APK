package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.EditableExifFields
import com.example.data.ExifCommandMode
import com.example.data.ImageMetadataReport
import com.example.data.MetadataDirectoryGroup
import com.example.data.MetadataRepository
import com.example.data.MetadataTag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InspectorTab {
    OVERVIEW,
    ALL_TAGS,
    EDITOR
}

sealed interface ExifLensEvent {
    data class ShowMessage(val message: String) : ExifLensEvent
    data class ShareImage(val uri: Uri, val mimeType: String) : ExifLensEvent
    data class RequestSafExport(val suggestedName: String, val mimeType: String) : ExifLensEvent
    data class CopyToClipboard(val label: String, val text: String) : ExifLensEvent
}

data class ExifLensUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val report: ImageMetadataReport? = null,
    val errorMessage: String? = null,
    val selectedTab: InspectorTab = InspectorTab.OVERVIEW,
    val searchQuery: String = "",
    val selectedDirectoryFilter: String? = null,
    val editDraft: EditableExifFields = EditableExifFields(),
    val deepReencodeScrub: Boolean = true,
    val darkThemeOverride: Boolean? = null
) {
    val filteredDirectories: List<MetadataDirectoryGroup>
        get() {
            val dirs = report?.directories.orEmpty()
            val dirFiltered = if (selectedDirectoryFilter == null) {
                dirs
            } else {
                dirs.filter { it.name == selectedDirectoryFilter }
            }
            val query = searchQuery.trim()
            if (query.isEmpty()) return dirFiltered
            return dirFiltered.mapNotNull { group ->
                val matching = group.tags.filter {
                    it.tagName.contains(query, ignoreCase = true) ||
                        it.value.contains(query, ignoreCase = true) ||
                        it.directory.contains(query, ignoreCase = true)
                }
                if (matching.isEmpty()) null else group.copy(tags = matching)
            }
        }
}

class ExifLensViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MetadataRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ExifLensUiState())
    val uiState: StateFlow<ExifLensUiState> = _uiState.asStateFlow()

    val exifTags: StateFlow<List<MetadataTag>> = _uiState
        .map { state ->
            state.report?.directories
                ?.flatMap { it.tags }
                .orEmpty()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _events = MutableSharedFlow<ExifLensEvent>()
    val events: SharedFlow<ExifLensEvent> = _events.asSharedFlow()

    fun toggleTheme(currentlyDark: Boolean) {
        _uiState.update { it.copy(darkThemeOverride = !currentlyDark) }
    }

    fun selectTab(tab: InspectorTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectDirectoryFilter(directory: String?) {
        _uiState.update { it.copy(selectedDirectoryFilter = directory) }
    }

    fun setDeepReencodeScrub(enabled: Boolean) {
        _uiState.update { it.copy(deepReencodeScrub = enabled) }
    }

    fun updateEditDraft(transform: (EditableExifFields) -> EditableExifFields) {
        _uiState.update { it.copy(editDraft = transform(it.editDraft)) }
    }

    fun fillCurrentDateTime() {
        val now = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
        updateEditDraft { it.copy(dateTimeOriginal = now) }
    }

    fun clearDraftGps() {
        updateEditDraft { it.copy(latitude = "", longitude = "") }
    }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            repository.loadImageFromUri(uri)
                .onSuccess { report ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            report = report,
                            editDraft = report.editableFields,
                            selectedDirectoryFilter = null,
                            searchQuery = "",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "تعذر قراءة الصورة"
                        )
                    }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "تعذر قراءة الصورة"))
                }
        }
    }

    fun applyMetadataEdits() {
        val current = _uiState.value.report ?: return
        val draft = _uiState.value.editDraft
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.applyEdits(current, draft)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            report = updated,
                            editDraft = updated.editableFields
                        )
                    }
                    _events.emit(ExifLensEvent.ShowMessage("تم تحديث البيانات الوصفية بنجاح"))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "فشل تعديل البيانات"))
                }
        }
    }

    fun stripGpsOnly() {
        val current = _uiState.value.report ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.stripGpsOnly(current)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            report = updated,
                            editDraft = updated.editableFields
                        )
                    }
                    _events.emit(ExifLensEvent.ShowMessage("تم مسح بيانات الموقع الجغرافي (GPS) بالكامل"))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "تعذر مسح الموقع"))
                }
        }
    }

    fun stripAllMetadata() {
        val current = _uiState.value.report ?: return
        val previousCount = current.totalTagCount
        val deepReencode = _uiState.value.deepReencodeScrub
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.stripAllMetadata(current, deepReencode)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            report = updated,
                            editDraft = updated.editableFields,
                            selectedDirectoryFilter = null
                        )
                    }
                    val removed = (previousCount - updated.totalTagCount).coerceAtLeast(0)
                    _events.emit(
                        ExifLensEvent.ShowMessage("تم تنظيف الصورة وإزالة $removed وسم وصفي")
                    )
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "فشل تنظيف الصورة"))
                }
        }
    }

    fun saveToGallery() {
        val current = _uiState.value.report ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.saveToGallery(current)
                .onSuccess {
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(
                        ExifLensEvent.ShowMessage("تم حفظ الصورة في المعرض (Pictures/ExifLens)")
                    )
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    if (err.message == "API_BELOW_29_USE_SAF") {
                        _events.emit(
                            ExifLensEvent.RequestSafExport(current.fileName, current.mimeType)
                        )
                    } else {
                        _events.emit(ExifLensEvent.ShowMessage(err.message ?: "تعذر الحفظ في المعرض"))
                    }
                }
        }
    }

    fun requestExportFile() {
        val current = _uiState.value.report ?: return
        viewModelScope.launch {
            _events.emit(ExifLensEvent.RequestSafExport(current.fileName, current.mimeType))
        }
    }

    fun exportToUri(destinationUri: Uri) {
        val current = _uiState.value.report ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.exportToUri(current, destinationUri)
                .onSuccess {
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage("تم تصدير الصورة بنجاح"))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "فشل تصدير الملف"))
                }
        }
    }

    fun shareCurrentImage() {
        val current = _uiState.value.report ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            repository.prepareShareUri(current)
                .onSuccess { uri ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShareImage(uri, current.mimeType))
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isProcessing = false) }
                    _events.emit(ExifLensEvent.ShowMessage(err.message ?: "تعذر تجهيز الصورة للمشاركة"))
                }
        }
    }

    fun copyFullReport() {
        val current = _uiState.value.report ?: return
        val text = repository.formatTextReport(current)
        viewModelScope.launch {
            _events.emit(ExifLensEvent.CopyToClipboard("ExifLens Report", text))
            _events.emit(ExifLensEvent.ShowMessage("تم نسخ تقرير البيانات الوصفية"))
        }
    }

    fun copyExifToolCommand(mode: ExifCommandMode) {
        val current = _uiState.value.report ?: return
        val cmd = repository.buildExifToolCommand(_uiState.value.editDraft, mode, current.fileName)
        viewModelScope.launch {
            _events.emit(ExifLensEvent.CopyToClipboard("ExifTool Command", cmd))
            _events.emit(ExifLensEvent.ShowMessage("تم نسخ أمر ExifTool"))
        }
    }

    fun previewExifToolCommand(mode: ExifCommandMode): String {
        val fileName = _uiState.value.report?.fileName ?: "photo.jpg"
        return repository.buildExifToolCommand(_uiState.value.editDraft, mode, fileName)
    }

    fun copyText(label: String, value: String) {
        viewModelScope.launch {
            _events.emit(ExifLensEvent.CopyToClipboard(label, value))
            _events.emit(ExifLensEvent.ShowMessage("تم النسخ: $label"))
        }
    }
}
