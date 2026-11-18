package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ExifCommandMode
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExifLensScreen(
    viewModel: ExifLensViewModel,
    isDarkTheme: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSelected(uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSelected(uri)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null) {
            viewModel.exportToUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExifLensEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ExifLensEvent.CopyToClipboard -> {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(event.label, event.text))
                }
                is ExifLensEvent.RequestSafExport -> {
                    runCatching {
                        createDocumentLauncher.launch(event.suggestedName)
                    }.onFailure {
                        snackbarHostState.showSnackbar("لا يتوفر مدير ملفات لحفظ النسخة على هذا الجهاز")
                    }
                }
                is ExifLensEvent.ShareImage -> {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.action_share))
                        )
                    }.onFailure {
                        snackbarHostState.showSnackbar("لا يوجد تطبيق متاح لمشاركة الصورة")
                    }
                }
            }
        }
    }

    val launchRawPicker = {
        runCatching {
            openDocumentLauncher.launch(arrayOf("image/*"))
        }
        Unit
    }

    val launchGalleryPicker = {
        runCatching {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            launchRawPicker()
        }
        Unit
    }

    val openMapForCoordinates: (Double, Double) -> Unit = { lat, lng ->
        val geoUri = Uri.parse(
            String.format(Locale.US, "geo:%.6f,%.6f?q=%.6f,%.6f(Photo+Location)", lat, lng, lat, lng)
        )
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
        runCatching {
            context.startActivity(mapIntent)
        }.onFailure {
            val webUri = Uri.parse(
                String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", lat, lng)
            )
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, webUri)) }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp && uiState.report != null

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.app_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.report != null) {
                            IconButton(
                                onClick = launchRawPicker,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_raw_picker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = stringResource(R.string.action_pick_raw)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.copyFullReport() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("topbar_copy_report_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = stringResource(R.string.action_export_report)
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleTheme(isDarkTheme) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "تبديل المظهر"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (uiState.report != null && !useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        InspectorTab.entries.forEach { tab ->
                            val selected = uiState.selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = tabTitle(tab)
                                    )
                                },
                                label = { Text(tabTitle(tab)) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = uiState.isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                                Text(
                                    text = "جاري تحليل البيانات الوصفية...",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        }
                    }

                    uiState.report == null -> {
                        EmptyStateScreen(
                            onPickFromGallery = launchGalleryPicker,
                            onPickRawDocument = launchRawPicker
                        )
                    }

                    else -> {
                        val activeReport = uiState.report!!
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (useNavigationRail) {
                                NavigationRail(
                                    modifier = Modifier.fillMaxHeight(),
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    InspectorTab.entries.forEach { tab ->
                                        val selected = uiState.selectedTab == tab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = tabTitle(tab)
                                                )
                                            },
                                            label = { Text(tabTitle(tab)) },
                                            modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                val header: @Composable () -> Unit = {
                                    PhotoSummaryHeaderCard(
                                        report = activeReport,
                                        onChangePhoto = launchGalleryPicker,
                                        onSaveToGallery = { viewModel.saveToGallery() },
                                        onShare = { viewModel.shareCurrentImage() }
                                    )
                                }

                                when (uiState.selectedTab) {
                                    InspectorTab.OVERVIEW -> {
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 720.dp)
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            header()
                                            OverviewTab(
                                                report = activeReport,
                                                inspectCommand = viewModel.previewExifToolCommand(
                                                    ExifCommandMode.INSPECT
                                                ),
                                                onStripGps = { viewModel.stripGpsOnly() },
                                                onStripAll = { viewModel.stripAllMetadata() },
                                                onOpenMap = openMapForCoordinates,
                                                onCopyText = { label, value ->
                                                    viewModel.copyText(label, value)
                                                },
                                                onCopyInspectCommand = {
                                                    viewModel.copyExifToolCommand(
                                                        ExifCommandMode.INSPECT
                                                    )
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    InspectorTab.ALL_TAGS -> {
                                        AllTagsTab(
                                            report = activeReport,
                                            filteredDirectories = uiState.filteredDirectories,
                                            searchQuery = uiState.searchQuery,
                                            selectedDirectory = uiState.selectedDirectoryFilter,
                                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                            onSelectDirectory = { viewModel.selectDirectoryFilter(it) },
                                            onCopyTag = { tag ->
                                                viewModel.copyText(tag.tagName, tag.value)
                                            },
                                            headerContent = header,
                                            modifier = Modifier.widthIn(max = 720.dp)
                                        )
                                    }

                                    InspectorTab.EDITOR -> {
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 720.dp)
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            header()
                                            EditorTab(
                                                report = activeReport,
                                                draft = uiState.editDraft,
                                                deepReencodeScrub = uiState.deepReencodeScrub,
                                                editCommandPreview = viewModel.previewExifToolCommand(
                                                    ExifCommandMode.EDIT
                                                ),
                                                stripCommandPreview = viewModel.previewExifToolCommand(
                                                    ExifCommandMode.STRIP_ALL
                                                ),
                                                onDraftChange = { viewModel.updateEditDraft(it) },
                                                onFillNow = { viewModel.fillCurrentDateTime() },
                                                onClearDraftGps = { viewModel.clearDraftGps() },
                                                onDeepReencodeChange = {
                                                    viewModel.setDeepReencodeScrub(it)
                                                },
                                                onApplyEdits = { viewModel.applyMetadataEdits() },
                                                onStripGps = { viewModel.stripGpsOnly() },
                                                onStripAll = { viewModel.stripAllMetadata() },
                                                onSaveToGallery = { viewModel.saveToGallery() },
                                                onExportFile = { viewModel.requestExportFile() },
                                                onShare = { viewModel.shareCurrentImage() },
                                                onCopyCommand = { mode ->
                                                    viewModel.copyExifToolCommand(mode)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun tabTitle(tab: InspectorTab): String = when (tab) {
    InspectorTab.OVERVIEW -> stringResource(R.string.tab_overview)
    InspectorTab.ALL_TAGS -> stringResource(R.string.tab_all_tags)
    InspectorTab.EDITOR -> stringResource(R.string.tab_editor)
}

private fun tabIcon(tab: InspectorTab, selected: Boolean) = when (tab) {
    InspectorTab.OVERVIEW -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
    InspectorTab.ALL_TAGS -> if (selected) Icons.Filled.DataObject else Icons.Outlined.DataObject
    InspectorTab.EDITOR -> if (selected) Icons.Filled.Edit else Icons.Outlined.Edit
}
