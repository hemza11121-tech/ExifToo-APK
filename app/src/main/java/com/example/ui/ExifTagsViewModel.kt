package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifDirectoryBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.util.Locale

data class ExifTag(
    val directory: String,
    val tagName: String,
    val tagValue: String,
    val tagIdHex: String
)

sealed interface ExifTagsUiState {
    data object Idle : ExifTagsUiState
    data object Loading : ExifTagsUiState
    data class Success(val tags: List<ExifTag>) : ExifTagsUiState
    data class Error(val message: String) : ExifTagsUiState
}

class ExifTagsViewModel(application: Application) : AndroidViewModel(application) {

    private val _exifTags = MutableStateFlow<List<ExifTag>>(emptyList())
    val exifTags: StateFlow<List<ExifTag>> = _exifTags.asStateFlow()

    private val _uiState = MutableStateFlow<ExifTagsUiState>(ExifTagsUiState.Idle)
    val uiState: StateFlow<ExifTagsUiState> = _uiState.asStateFlow()

    fun loadExifTags(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ExifTagsUiState.Loading
            runCatching {
                readExifTagsFromUri(uri)
            }.onSuccess { tags ->
                _exifTags.value = tags
                _uiState.value = ExifTagsUiState.Success(tags)
            }.onFailure { throwable ->
                _exifTags.value = emptyList()
                _uiState.value = ExifTagsUiState.Error(
                    throwable.message ?: "Failed to read EXIF tags from URI"
                )
            }
        }
    }

    fun clear() {
        _exifTags.value = emptyList()
        _uiState.value = ExifTagsUiState.Idle
    }

    private suspend fun readExifTagsFromUri(uri: Uri): List<ExifTag> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        resolver.openInputStream(uri)?.use { rawStream ->
            BufferedInputStream(rawStream).use { bufferedStream ->
                val metadata = ImageMetadataReader.readMetadata(bufferedStream)
                metadata.directories
                    .filterIsInstance<ExifDirectoryBase>()
                    .flatMap { directory ->
                        directory.tags.mapNotNull { tag ->
                            val value = tag.description?.trim()
                            if (value.isNullOrEmpty()) {
                                null
                            } else {
                                ExifTag(
                                    directory = directory.name,
                                    tagName = tag.tagName,
                                    tagValue = value,
                                    tagIdHex = String.format(Locale.US, "0x%04X", tag.tagType)
                                )
                            }
                        }
                    }
            }
        } ?: error("Unable to open input stream for URI: $uri")
    }
}
