package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.data.EditableExifFields
import com.example.data.ExifCommandMode
import com.example.data.MetadataRepository
import com.example.data.PrivacyRiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ExifLens", appName)
    }

    @Test
    fun `inspect edit stripGps and deepScrub workflow works end to end`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MetadataRepository(context)

        // Create a real JPEG file with EXIF & GPS tags
        val sampleFile = File(context.cacheDir, "sample_test.jpg")
        val bmp = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        FileOutputStream(sampleFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()

        val exif = ExifInterface(sampleFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "Google")
        exif.setAttribute(ExifInterface.TAG_MODEL, "Pixel 9 Pro")
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:11:17 12:30:00")
        exif.setLatLong(24.713552, 46.675296)
        exif.saveAttributes()

        // 1. Load & inspect
        val loaded = repo.loadImageFromUri(Uri.fromFile(sampleFile)).getOrThrow()
        assertEquals("Google", loaded.cameraSummary.make)
        assertEquals("Pixel 9 Pro", loaded.cameraSummary.model)
        assertEquals(PrivacyRiskLevel.HIGH_GPS_AND_DEVICE, loaded.privacyRiskLevel)
        assertNotNull(loaded.gpsLatitude)
        assertNotNull(loaded.gpsLongitude)
        assertEquals(24.713552, loaded.gpsLatitude!!, 0.001)
        assertEquals(46.675296, loaded.gpsLongitude!!, 0.001)

        // 2. Edit metadata
        val edited = repo.applyEdits(
            loaded,
            EditableExifFields(
                make = "Sony",
                model = "Alpha 7 IV",
                dateTimeOriginal = "2026:11:17 14:00:00",
                artist = "Hamza",
                copyright = "2026",
                description = "Test Shot",
                userComment = "Clean",
                latitude = "24.713552",
                longitude = "46.675296"
            )
        ).getOrThrow()
        assertEquals("Sony", edited.cameraSummary.make)
        assertEquals("Alpha 7 IV", edited.cameraSummary.model)
        assertEquals("Hamza", edited.editableFields.artist)
        assertTrue(edited.isModified)

        // 3. Strip GPS only
        val noGps = repo.stripGpsOnly(edited).getOrThrow()
        assertNull(noGps.gpsLatitude)
        assertNull(noGps.gpsLongitude)
        assertEquals(PrivacyRiskLevel.MEDIUM_DEVICE_ONLY, noGps.privacyRiskLevel)
        assertEquals("Sony", noGps.cameraSummary.make)

        // 4. Deep scrub all metadata
        val scrubbed = repo.stripAllMetadata(noGps, deepReencode = true).getOrThrow()
        assertNull(scrubbed.gpsLatitude)
        assertNull(scrubbed.cameraSummary.make)
        assertNull(scrubbed.cameraSummary.model)
        assertEquals(PrivacyRiskLevel.CLEAN, scrubbed.privacyRiskLevel)

        // 5. ExifTool command builder
        val cmd = repo.buildExifToolCommand(
            edited.editableFields,
            ExifCommandMode.STRIP_ALL,
            "sample_test.jpg"
        )
        assertEquals("exiftool -all= -overwrite_original \"sample_test.jpg\"", cmd)
    }

    @Test
    fun `ExifTagsViewModel reads EXIF tags from selected URI using metadata-extractor`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sampleFile = File(context.cacheDir, "vm_exif_test.jpg")
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        FileOutputStream(sampleFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bmp.recycle()

        val exif = ExifInterface(sampleFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, "Canon")
        exif.setAttribute(ExifInterface.TAG_MODEL, "EOS R5")
        exif.setLatLong(30.0444, 31.2357)
        exif.saveAttributes()

        val viewModel = com.example.ui.ExifTagsViewModel(context)
        viewModel.loadExifTags(Uri.fromFile(sampleFile))

        // Idle the Robolectric main looper so viewModelScope can resume after Dispatchers.IO
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.exifTags.value.isEmpty() && System.currentTimeMillis() < deadline) {
            mainLooper.idle()
            Thread.sleep(25)
        }
        mainLooper.idle()

        val tags = viewModel.exifTags.value
        assertTrue(tags.isNotEmpty())
        assertTrue(tags.any { it.tagName == "Make" && it.tagValue == "Canon" })
        assertTrue(tags.any { it.tagName == "Model" && it.tagValue == "EOS R5" })
        assertTrue(tags.any { it.directory.contains("GPS", ignoreCase = true) })
    }
}
