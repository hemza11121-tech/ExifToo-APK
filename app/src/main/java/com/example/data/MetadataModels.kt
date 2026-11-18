package com.example.data

enum class PrivacyRiskLevel {
    HIGH_GPS_AND_DEVICE,
    MEDIUM_DEVICE_ONLY,
    CLEAN
}

enum class ExifCommandMode {
    INSPECT,
    EDIT,
    STRIP_GPS,
    STRIP_ALL
}

data class MetadataTag(
    val directory: String,
    val tagName: String,
    val value: String,
    val tagTypeHex: String
)

data class MetadataDirectoryGroup(
    val name: String,
    val tags: List<MetadataTag>
)

data class EditableExifFields(
    val make: String = "",
    val model: String = "",
    val dateTimeOriginal: String = "",
    val artist: String = "",
    val copyright: String = "",
    val description: String = "",
    val userComment: String = "",
    val latitude: String = "",
    val longitude: String = ""
)

data class CameraSummary(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val flash: String? = null,
    val whiteBalance: String? = null,
    val software: String? = null
)

data class ImageMetadataReport(
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val totalTagCount: Int,
    val meaningfulExifTagCount: Int,
    val cameraSummary: CameraSummary,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAltitude: String?,
    val dateTimeTaken: String?,
    val privacyRiskLevel: PrivacyRiskLevel,
    val directories: List<MetadataDirectoryGroup>,
    val editableFields: EditableExifFields,
    val workingFilePath: String,
    val isModified: Boolean = false,
    val supportsDirectExifWrite: Boolean = true
)
