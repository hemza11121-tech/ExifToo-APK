package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Iso
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExifCommandMode
import com.example.data.ImageMetadataReport

@Composable
fun OverviewTab(
    report: ImageMetadataReport,
    inspectCommand: String,
    onStripGps: () -> Unit,
    onStripAll: () -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onCopyText: (String, String) -> Unit,
    onCopyInspectCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PrivacyAuditBanner(
            report = report,
            onStripGps = onStripGps,
            onStripAll = onStripAll,
            onOpenMap = onOpenMap,
            onCopyCoords = { coords -> onCopyText("GPS", coords) }
        )

        SectionTitleRow(
            icon = Icons.Default.CameraAlt,
            title = stringResource(R.string.label_camera)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "الشركة المصنعة",
                value = report.cameraSummary.make,
                icon = Icons.Default.Apartment,
                onCopy = { onCopyText("Make", it) },
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "طراز الجهاز",
                value = report.cameraSummary.model,
                icon = Icons.Default.CameraAlt,
                onCopy = { onCopyText("Model", it) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "العدسة (Lens)",
                value = report.cameraSummary.lens,
                icon = Icons.Default.Camera,
                onCopy = { onCopyText("Lens", it) },
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "تاريخ الالتقاط",
                value = report.dateTimeTaken,
                icon = Icons.Default.CalendarToday,
                onCopy = { onCopyText("Date", it) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitleRow(
            icon = Icons.Default.ShutterSpeed,
            title = stringResource(R.string.label_exposure)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "فتحة العدسة",
                value = report.cameraSummary.aperture,
                icon = Icons.Default.Camera,
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "سرعة الغالق",
                value = report.cameraSummary.shutterSpeed,
                icon = Icons.Default.ShutterSpeed,
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "ISO",
                value = report.cameraSummary.iso,
                icon = Icons.Default.Iso,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "البعد البؤري",
                value = report.cameraSummary.focalLength,
                icon = Icons.Default.CenterFocusStrong,
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "الفلاش",
                value = report.cameraSummary.flash,
                icon = Icons.Default.FlashOn,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "توازن البياض",
                value = report.cameraSummary.whiteBalance,
                icon = Icons.Default.WbSunny,
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "النظام / البرنامج",
                value = report.cameraSummary.software,
                icon = Icons.Default.Memory,
                onCopy = { onCopyText("Software", it) },
                modifier = Modifier.weight(1f)
            )
        }

        SectionTitleRow(
            icon = Icons.Default.AspectRatio,
            title = stringResource(R.string.label_file_info)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetadataMetricTile(
                label = "الأبعاد",
                value = "${report.width} × ${report.height} px",
                icon = Icons.Default.AspectRatio,
                modifier = Modifier.weight(1f)
            )
            MetadataMetricTile(
                label = "حجم الملف",
                value = formatFileSize(report.fileSizeBytes),
                icon = Icons.Default.Memory,
                modifier = Modifier.weight(1f)
            )
        }

        ExifToolCommandCard(
            title = stringResource(R.string.label_exiftool_cmd),
            command = inspectCommand,
            onCopy = onCopyInspectCommand
        )
    }
}
