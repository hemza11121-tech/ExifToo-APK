package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.EditableExifFields
import com.example.data.ExifCommandMode
import com.example.data.ImageMetadataReport
import com.example.ui.theme.MonoTextStyle
import com.example.ui.theme.RiskHighColor

@Composable
fun EditorTab(
    report: ImageMetadataReport,
    draft: EditableExifFields,
    deepReencodeScrub: Boolean,
    editCommandPreview: String,
    stripCommandPreview: String,
    onDraftChange: ((EditableExifFields) -> EditableExifFields) -> Unit,
    onFillNow: () -> Unit,
    onClearDraftGps: () -> Unit,
    onDeepReencodeChange: (Boolean) -> Unit,
    onApplyEdits: () -> Unit,
    onStripGps: () -> Unit,
    onStripAll: () -> Unit,
    onSaveToGallery: () -> Unit,
    onExportFile: () -> Unit,
    onShare: () -> Unit,
    onCopyCommand: (ExifCommandMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Privacy Scrub Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitleRow(
                    icon = Icons.Default.CleaningServices,
                    title = "حذف البيانات وحماية الخصوصية"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.action_deep_scrub),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "إزالة 100% من EXIF و XMP و IPTC والصورة المصغّرة المخفية عبر إعادة ترميز البكسلات.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = deepReencodeScrub,
                        onCheckedChange = onDeepReencodeChange,
                        modifier = Modifier.testTag("deep_scrub_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onStripGps,
                        enabled = report.gpsLatitude != null && report.gpsLongitude != null,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("editor_strip_gps_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_strip_gps), maxLines = 1)
                    }

                    Button(
                        onClick = onStripAll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RiskHighColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("editor_strip_all_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مسح الكل", maxLines = 1)
                    }
                }
            }
        }

        ExifToolCommandCard(
            title = "أمر ExifTool لحذف جميع البيانات",
            command = stripCommandPreview,
            onCopy = { onCopyCommand(ExifCommandMode.STRIP_ALL) }
        )

        // Metadata Editing Form
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تعديل وسوم EXIF يدوياً",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (!report.supportsDirectExifWrite) {
                    Text(
                        text = "ملاحظة: صيغة الملف الحالية (${report.mimeType}) سيتم تحويلها إلى JPEG عند تطبيق التعديلات لضمان دعم كتابة EXIF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.make,
                        onValueChange = { value -> onDraftChange { it.copy(make = value) } },
                        label = { Text("الشركة (Make)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_make")
                    )
                    OutlinedTextField(
                        value = draft.model,
                        onValueChange = { value -> onDraftChange { it.copy(model = value) } },
                        label = { Text("الطراز (Model)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_model")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = draft.dateTimeOriginal,
                            onValueChange = { value ->
                                onDraftChange { it.copy(dateTimeOriginal = value) }
                            },
                            label = { Text("DateTimeOriginal") },
                            placeholder = { Text("YYYY:MM:DD HH:MM:SS", style = MonoTextStyle) },
                            textStyle = MonoTextStyle,
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_datetime")
                        )
                    }
                    AssistChip(
                        onClick = onFillNow,
                        label = { Text("الآن") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("fill_now_chip")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = draft.artist,
                        onValueChange = { value -> onDraftChange { it.copy(artist = value) } },
                        label = { Text("المصور (Artist)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_artist")
                    )
                    OutlinedTextField(
                        value = draft.copyright,
                        onValueChange = { value -> onDraftChange { it.copy(copyright = value) } },
                        label = { Text("حقوق النشر (Copyright)") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_copyright")
                    )
                }

                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { value -> onDraftChange { it.copy(description = value) } },
                    label = { Text("وصف الصورة (ImageDescription)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_description")
                )

                OutlinedTextField(
                    value = draft.userComment,
                    onValueChange = { value -> onDraftChange { it.copy(userComment = value) } },
                    label = { Text("ملاحظات (UserComment)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_comment")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "إحداثيات الموقع (GPS)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    AssistChip(
                        onClick = onClearDraftGps,
                        label = { Text("تفريغ GPS") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("clear_draft_gps_chip")
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = draft.latitude,
                            onValueChange = { value -> onDraftChange { it.copy(latitude = value) } },
                            label = { Text("Latitude") },
                            placeholder = { Text("24.713552", style = MonoTextStyle) },
                            textStyle = MonoTextStyle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_latitude")
                        )
                        OutlinedTextField(
                            value = draft.longitude,
                            onValueChange = { value -> onDraftChange { it.copy(longitude = value) } },
                            label = { Text("Longitude") },
                            placeholder = { Text("46.675296", style = MonoTextStyle) },
                            textStyle = MonoTextStyle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_longitude")
                        )
                    }
                }

                Button(
                    onClick = onApplyEdits,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("apply_edits_button"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_apply_edits))
                }
            }
        }

        ExifToolCommandCard(
            title = "أمر ExifTool المكافئ للتعديل الحالي",
            command = editCommandPreview,
            onCopy = { onCopyCommand(ExifCommandMode.EDIT) }
        )

        // Export & Share Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "حفظ وتصدير الصورة",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSaveToGallery,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("editor_save_gallery_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save_gallery), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onExportFile,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("editor_export_saf_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_export_file), maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("editor_share_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
        }
    }
}
