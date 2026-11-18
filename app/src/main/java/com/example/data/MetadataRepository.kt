package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MetadataRepository(private val context: Context) {

    private val workingDir: File
        get() = File(context.cacheDir, "working").apply { mkdirs() }

    private val sharedDir: File
        get() = File(context.cacheDir, "shared").apply { mkdirs() }

    suspend fun loadImageFromUri(uri: Uri): Result<ImageMetadataReport> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val resolvedMime = resolver.getType(uri) ?: "image/jpeg"
            val displayName = queryDisplayName(uri) ?: buildDefaultFileName(resolvedMime)
            val ext = extensionFor(displayName, resolvedMime)

            // Clean old working files to prevent cache bloat
            workingDir.listFiles()?.forEach { it.delete() }
            val targetFile = File(workingDir, "active_${System.currentTimeMillis()}.$ext")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("تعذر فتح ملف الصورة المحدد")

            inspectFile(
                file = targetFile,
                displayName = displayName,
                mimeType = resolvedMime,
                isModified = false
            )
        }
    }

    suspend fun applyEdits(
        currentReport: ImageMetadataReport,
        edits: EditableExifFields
    ): Result<ImageMetadataReport> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(currentReport.workingFilePath)
            require(sourceFile.exists()) { "ملف العمل غير موجود، يرجى إعادة اختيار الصورة" }

            val targetMime = if (currentReport.supportsDirectExifWrite) {
                currentReport.mimeType
            } else {
                "image/jpeg"
            }
            val targetExt = extensionFor(currentReport.fileName, targetMime)
            val updatedFile = File(workingDir, "edited_${System.currentTimeMillis()}.$targetExt")

            if (currentReport.supportsDirectExifWrite) {
                sourceFile.copyTo(updatedFile, overwrite = true)
            } else {
                reencodeImage(sourceFile, updatedFile, Bitmap.CompressFormat.JPEG)
            }

            val exif = ExifInterface(updatedFile.absolutePath)
            setOrClearTag(exif, ExifInterface.TAG_MAKE, edits.make)
            setOrClearTag(exif, ExifInterface.TAG_MODEL, edits.model)
            setOrClearTag(exif, ExifInterface.TAG_DATETIME_ORIGINAL, edits.dateTimeOriginal)
            setOrClearTag(exif, ExifInterface.TAG_DATETIME, edits.dateTimeOriginal)
            setOrClearTag(exif, ExifInterface.TAG_ARTIST, edits.artist)
            setOrClearTag(exif, ExifInterface.TAG_COPYRIGHT, edits.copyright)
            setOrClearTag(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, edits.description)
            setOrClearTag(exif, ExifInterface.TAG_USER_COMMENT, edits.userComment)

            val lat = edits.latitude.trim().toDoubleOrNull()
            val lng = edits.longitude.trim().toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                exif.setLatLong(lat, lng)
            } else if (edits.latitude.isBlank() && edits.longitude.isBlank()) {
                clearGpsTags(exif)
            } else {
                error("إحداثيات GPS غير صالحة (خط العرض -90..90، خط الطول -180..180)")
            }

            exif.saveAttributes()

            val updatedDisplayName = if (currentReport.supportsDirectExifWrite) {
                currentReport.fileName
            } else {
                replaceExtension(currentReport.fileName, targetExt)
            }

            inspectFile(
                file = updatedFile,
                displayName = updatedDisplayName,
                mimeType = targetMime,
                isModified = true
            )
        }
    }

    suspend fun stripGpsOnly(currentReport: ImageMetadataReport): Result<ImageMetadataReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(currentReport.workingFilePath)
                require(sourceFile.exists()) { "ملف العمل غير موجود" }

                val targetMime = if (currentReport.supportsDirectExifWrite) {
                    currentReport.mimeType
                } else {
                    "image/jpeg"
                }
                val ext = extensionFor(currentReport.fileName, targetMime)
                val updatedFile = File(workingDir, "nogps_${System.currentTimeMillis()}.$ext")

                if (currentReport.supportsDirectExifWrite) {
                    sourceFile.copyTo(updatedFile, overwrite = true)
                    val exif = ExifInterface(updatedFile.absolutePath)
                    clearGpsTags(exif)
                    exif.saveAttributes()
                } else {
                    // Fallback re-encode if container doesn't support in-place ExifInterface write
                    reencodeImage(sourceFile, updatedFile, Bitmap.CompressFormat.JPEG)
                }

                inspectFile(
                    file = updatedFile,
                    displayName = replaceExtension(currentReport.fileName, ext),
                    mimeType = targetMime,
                    isModified = true
                )
            }
        }

    suspend fun stripAllMetadata(
        currentReport: ImageMetadataReport,
        deepReencode: Boolean
    ): Result<ImageMetadataReport> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(currentReport.workingFilePath)
            require(sourceFile.exists()) { "ملف العمل غير موجود" }

            val isPng = currentReport.mimeType.equals("image/png", ignoreCase = true)
            val outMime = if (isPng) "image/png" else "image/jpeg"
            val outExt = if (isPng) "png" else "jpg"
            val cleanedFile = File(workingDir, "clean_${System.currentTimeMillis()}.$outExt")

            if (deepReencode || !currentReport.supportsDirectExifWrite) {
                val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                reencodeImage(sourceFile, cleanedFile, format)
            } else {
                sourceFile.copyTo(cleanedFile, overwrite = true)
                val exif = ExifInterface(cleanedFile.absolutePath)
                clearAllStandardExifTags(exif)
                exif.saveAttributes()
            }

            val baseName = currentReport.fileName.substringBeforeLast('.')
            inspectFile(
                file = cleanedFile,
                displayName = "${baseName}_clean.$outExt",
                mimeType = outMime,
                isModified = true
            )
        }
    }

    suspend fun saveToGallery(report: ImageMetadataReport): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(report.workingFilePath)
            require(sourceFile.exists()) { "لا توجد صورة جاهزة للحفظ" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, report.fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, report.mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/ExifLens"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, values)
                    ?: error("تعذر إنشاء سجل الصورة في المعرض")

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    } ?: error("تعذر الكتابة إلى المعرض")

                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)
                    itemUri
                } catch (t: Throwable) {
                    resolver.delete(itemUri, null, null)
                    throw t
                }
            } else {
                error("API_BELOW_29_USE_SAF")
            }
        }
    }

    suspend fun exportToUri(report: ImageMetadataReport, destinationUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(report.workingFilePath)
                require(sourceFile.exists()) { "ملف العمل غير موجود" }
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: error("تعذر الكتابة إلى الوجهة المحددة")
                Unit
            }
        }

    suspend fun prepareShareUri(report: ImageMetadataReport): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(report.workingFilePath)
            require(sourceFile.exists()) { "لا توجد صورة للمشاركة" }
            sharedDir.listFiles()?.forEach { it.delete() }
            val safeName = report.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destFile = File(sharedDir, safeName)
            sourceFile.copyTo(destFile, overwrite = true)

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        }
    }

    fun buildExifToolCommand(
        edits: EditableExifFields,
        mode: ExifCommandMode,
        fileName: String
    ): String {
        val quotedFile = "\"${fileName.ifBlank { "photo.jpg" }}\""
        return when (mode) {
            ExifCommandMode.INSPECT -> "exiftool -G1 -a -s $quotedFile"
            ExifCommandMode.STRIP_GPS -> "exiftool -gps:all= $quotedFile"
            ExifCommandMode.STRIP_ALL -> "exiftool -all= -overwrite_original $quotedFile"
            ExifCommandMode.EDIT -> {
                val args = mutableListOf<String>()
                if (edits.make.isNotBlank()) args.add("-Make=\"${escapeArg(edits.make)}\"")
                if (edits.model.isNotBlank()) args.add("-Model=\"${escapeArg(edits.model)}\"")
                if (edits.dateTimeOriginal.isNotBlank()) {
                    args.add("-DateTimeOriginal=\"${escapeArg(edits.dateTimeOriginal)}\"")
                }
                if (edits.artist.isNotBlank()) args.add("-Artist=\"${escapeArg(edits.artist)}\"")
                if (edits.copyright.isNotBlank()) {
                    args.add("-Copyright=\"${escapeArg(edits.copyright)}\"")
                }
                if (edits.description.isNotBlank()) {
                    args.add("-ImageDescription=\"${escapeArg(edits.description)}\"")
                }
                if (edits.userComment.isNotBlank()) {
                    args.add("-UserComment=\"${escapeArg(edits.userComment)}\"")
                }
                val lat = edits.latitude.trim().toDoubleOrNull()
                val lng = edits.longitude.trim().toDoubleOrNull()
                if (lat != null && lng != null) {
                    val latRef = if (lat >= 0) "N" else "S"
                    val lngRef = if (lng >= 0) "E" else "W"
                    args.add("-GPSLatitude=${kotlin.math.abs(lat)}")
                    args.add("-GPSLatitudeRef=$latRef")
                    args.add("-GPSLongitude=${kotlin.math.abs(lng)}")
                    args.add("-GPSLongitudeRef=$lngRef")
                } else if (edits.latitude.isBlank() && edits.longitude.isBlank()) {
                    args.add("-gps:all=")
                }

                if (args.isEmpty()) {
                    "exiftool -G1 -a -s $quotedFile"
                } else {
                    "exiftool ${args.joinToString(" ")} $quotedFile"
                }
            }
        }
    }

    fun formatTextReport(report: ImageMetadataReport): String = buildString {
        appendLine("=== ExifLens Metadata Report ===")
        appendLine("File: ${report.fileName}")
        appendLine("MIME: ${report.mimeType}")
        appendLine("Dimensions: ${report.width} x ${report.height}")
        appendLine("Size: ${report.fileSizeBytes} bytes")
        appendLine("Total Tags: ${report.totalTagCount}")
        if (report.gpsLatitude != null && report.gpsLongitude != null) {
            appendLine(
                String.format(
                    Locale.US,
                    "GPS: %.6f, %.6f",
                    report.gpsLatitude,
                    report.gpsLongitude
                )
            )
        } else {
            appendLine("GPS: None")
        }
        appendLine()
        report.directories.forEach { dir ->
            appendLine("[${dir.name}]")
            dir.tags.forEach { tag ->
                appendLine("  ${tag.tagName}: ${tag.value}")
            }
            appendLine()
        }
    }

    private fun inspectFile(
        file: File,
        displayName: String,
        mimeType: String,
        isModified: Boolean
    ): ImageMetadataReport {
        val directories = mutableListOf<MetadataDirectoryGroup>()
        var make: String? = null
        var model: String? = null
        var lens: String? = null
        var focalLength: String? = null
        var aperture: String? = null
        var shutterSpeed: String? = null
        var iso: String? = null
        var flash: String? = null
        var whiteBalance: String? = null
        var software: String? = null
        var dateTimeTaken: String? = null
        var gpsLat: Double? = null
        var gpsLng: Double? = null
        var gpsAltitude: String? = null
        var meaningfulExifTags = 0

        val metadata = runCatching { ImageMetadataReader.readMetadata(file) }.getOrNull()
        metadata?.directories?.forEach { dir ->
            val tags = dir.tags.mapNotNull { tag ->
                val desc = tag.description?.trim()
                if (desc.isNullOrEmpty()) return@mapNotNull null
                MetadataTag(
                    directory = dir.name,
                    tagName = tag.tagName,
                    value = desc,
                    tagTypeHex = String.format(Locale.US, "0x%04X", tag.tagType)
                )
            }
            if (tags.isNotEmpty()) {
                directories.add(MetadataDirectoryGroup(name = dir.name, tags = tags))
            }

            if (dir is ExifIFD0Directory) {
                meaningfulExifTags += tags.size
                make = dir.getDescription(ExifIFD0Directory.TAG_MAKE) ?: make
                model = dir.getDescription(ExifIFD0Directory.TAG_MODEL) ?: model
                software = dir.getDescription(ExifIFD0Directory.TAG_SOFTWARE) ?: software
                if (dateTimeTaken == null) {
                    dateTimeTaken = dir.getDescription(ExifIFD0Directory.TAG_DATETIME)
                }
            }

            if (dir is ExifSubIFDDirectory) {
                meaningfulExifTags += tags.size
                lens = dir.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL) ?: lens
                focalLength = dir.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH) ?: focalLength
                aperture = dir.getDescription(ExifSubIFDDirectory.TAG_FNUMBER)
                    ?: dir.getDescription(ExifSubIFDDirectory.TAG_APERTURE)
                    ?: aperture
                shutterSpeed = dir.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)
                    ?: dir.getDescription(ExifSubIFDDirectory.TAG_SHUTTER_SPEED)
                    ?: shutterSpeed
                iso = dir.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT) ?: iso
                flash = dir.getDescription(ExifSubIFDDirectory.TAG_FLASH) ?: flash
                whiteBalance = dir.getDescription(ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE)
                    ?: dir.getDescription(ExifSubIFDDirectory.TAG_WHITE_BALANCE)
                    ?: whiteBalance
                dateTimeTaken = dir.getDescription(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
                    ?: dateTimeTaken
            }

            if (dir is GpsDirectory) {
                meaningfulExifTags += tags.size
                val loc = dir.geoLocation
                if (loc != null && !loc.isZero) {
                    gpsLat = loc.latitude
                    gpsLng = loc.longitude
                }
                gpsAltitude = dir.getDescription(GpsDirectory.TAG_ALTITUDE) ?: gpsAltitude
            }

            if (dir.name.contains("XMP", ignoreCase = true) ||
                dir.name.contains("IPTC", ignoreCase = true)
            ) {
                meaningfulExifTags += tags.size
            }
        }

        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        if (exif != null) {
            val exifLatLong = exif.latLong
            if (gpsLat == null && exifLatLong != null && exifLatLong.size == 2) {
                gpsLat = exifLatLong[0]
                gpsLng = exifLatLong[1]
            }
            make = make ?: exif.getAttribute(ExifInterface.TAG_MAKE)
            model = model ?: exif.getAttribute(ExifInterface.TAG_MODEL)
            dateTimeTaken = dateTimeTaken ?: exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            software = software ?: exif.getAttribute(ExifInterface.TAG_SOFTWARE)
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth.coerceAtLeast(0)
        val height = bounds.outHeight.coerceAtLeast(0)

        val editableFields = EditableExifFields(
            make = exif?.getAttribute(ExifInterface.TAG_MAKE).orEmpty(),
            model = exif?.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
            dateTimeOriginal = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif?.getAttribute(ExifInterface.TAG_DATETIME).orEmpty(),
            artist = exif?.getAttribute(ExifInterface.TAG_ARTIST).orEmpty(),
            copyright = exif?.getAttribute(ExifInterface.TAG_COPYRIGHT).orEmpty(),
            description = exif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).orEmpty(),
            userComment = exif?.getAttribute(ExifInterface.TAG_USER_COMMENT).orEmpty(),
            latitude = gpsLat?.let { String.format(Locale.US, "%.6f", it) }.orEmpty(),
            longitude = gpsLng?.let { String.format(Locale.US, "%.6f", it) }.orEmpty()
        )

        val hasPrivacyData = meaningfulExifTags > 0 ||
            editableFields.make.isNotBlank() ||
            editableFields.model.isNotBlank() ||
            editableFields.artist.isNotBlank() ||
            editableFields.dateTimeOriginal.isNotBlank()

        val riskLevel = when {
            gpsLat != null && gpsLng != null -> PrivacyRiskLevel.HIGH_GPS_AND_DEVICE
            hasPrivacyData -> PrivacyRiskLevel.MEDIUM_DEVICE_ONLY
            else -> PrivacyRiskLevel.CLEAN
        }

        val supportsDirectWrite = mimeType.lowercase(Locale.US) in setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
        )

        return ImageMetadataReport(
            fileName = displayName,
            fileSizeBytes = file.length(),
            mimeType = mimeType,
            width = width,
            height = height,
            totalTagCount = directories.sumOf { it.tags.size },
            meaningfulExifTagCount = meaningfulExifTags,
            cameraSummary = CameraSummary(
                make = make,
                model = model,
                lens = lens,
                focalLength = focalLength,
                aperture = aperture,
                shutterSpeed = shutterSpeed,
                iso = iso,
                flash = flash,
                whiteBalance = whiteBalance,
                software = software
            ),
            gpsLatitude = gpsLat,
            gpsLongitude = gpsLng,
            gpsAltitude = gpsAltitude,
            dateTimeTaken = dateTimeTaken,
            privacyRiskLevel = riskLevel,
            directories = directories,
            editableFields = editableFields,
            workingFilePath = file.absolutePath,
            isModified = isModified,
            supportsDirectExifWrite = supportsDirectWrite
        )
    }

    private fun reencodeImage(source: File, dest: File, format: Bitmap.CompressFormat) {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("تعذر فك ترميز الصورة")

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
        }

        val finalBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        try {
            FileOutputStream(dest).use { out ->
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 96
                finalBitmap.compress(format, quality, out)
            }
        } finally {
            finalBitmap.recycle()
        }
    }

    private fun setOrClearTag(exif: ExifInterface, tag: String, value: String) {
        val trimmed = value.trim()
        exif.setAttribute(tag, trimmed.ifEmpty { null })
    }

    private fun clearGpsTags(exif: ExifInterface) {
        val gpsTags = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE
        )
        gpsTags.forEach { exif.setAttribute(it, null) }
    }

    private fun clearAllStandardExifTags(exif: ExifInterface) {
        clearGpsTags(exif)
        val tagsToClear = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_SPECIFICATION,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_XMP
        )
        tagsToClear.forEach { exif.setAttribute(it, null) }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return null
    }

    private fun buildDefaultFileName(mimeType: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        return "image_${System.currentTimeMillis()}.$ext"
    }

    private fun extensionFor(fileName: String, mimeType: String): String {
        val fromName = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (fromName.isNotEmpty() && fromName.length <= 5) return fromName
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
    }

    private fun replaceExtension(fileName: String, newExt: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        return "$base.$newExt"
    }

    private fun escapeArg(value: String): String = value.replace("\"", "\\\"")
}
