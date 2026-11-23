package com.yuval.podcasts.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.yuval.podcasts.R
import com.yuval.podcasts.data.Constants
import com.yuval.podcasts.ui.viewmodel.AppUpdateState
import com.yuval.podcasts.ui.viewmodel.SettingsUiState
import androidx.compose.ui.focus.onFocusChanged
import androidx.work.WorkInfo
import com.yuval.podcasts.BuildConfig
import com.yuval.podcasts.ui.components.RefreshableContent
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt


private const val KEYBOARD_ANIMATION_DELAY_PART1_MS = 150L
private const val KEYBOARD_ANIMATION_DELAY_PART2_MS = 200L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAddPodcast: (String) -> Unit,
    onImportOpml: (Uri) -> Unit,
    onExportOpml: (Uri) -> Unit,
    onImportLocalAudio: (Uri) -> Unit,
    onLogNoteChanged: (String) -> Unit,
    onSaveLogNote: () -> Unit,
    onDownloadLogs: (Uri) -> Unit,
    onClearError: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onCancelUpdateDownload: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onInstallLaunched: () -> Unit = {},
    onInstallError: (String) -> Unit = {},
    isRefreshing: Boolean = false,
    refreshProgress: Pair<Int, Int>? = null,
    onRefreshAll: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var rssUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var isLogNoteFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isLogNoteFocused, isImeVisible) {
        if (isLogNoteFocused && isImeVisible) {
            // Animate scroll to bottom as layout updates and keyboard slides up.
            // We run this at two intervals (150ms and 350ms) to ensure it reaches the true
            // settled maximum scroll value since the keyboard animation takes some time.
            delay(KEYBOARD_ANIMATION_DELAY_PART1_MS)
            scrollState.animateScrollTo(scrollState.maxValue)
            delay(KEYBOARD_ANIMATION_DELAY_PART2_MS)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }


    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error.asString(context))
            onClearError()
        }
        uiState.successMessage?.let { success ->
            snackbarHostState.showSnackbar(success.asString(context))
            onClearError()
        }
    }

    val opmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpml(it) } }
    )

    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(Constants.MIME_TYPE_OPML),
        onResult = { uri -> uri?.let { onExportOpml(it) } }
    )

    val localAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportLocalAudio(it) } }
    )

    val logExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(Constants.MIME_TYPE_JSONL),
        onResult = { uri -> uri?.let { onDownloadLogs(it) } }
    )

    var pendingPermissionApkPath by rememberSaveable { mutableStateOf<String?>(null) }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            val path = pendingPermissionApkPath
            pendingPermissionApkPath = null
            if (path != null && context.packageManager.canRequestPackageInstalls()) {
                val apk = File(path)
                if (apk.exists() && apk.length() > 0L) {
                    launchApkInstaller(context, apk)?.let(onInstallError)
                } else {
                    onInstallUpdate()
                }
            }
        }
    )

    LaunchedEffect(uiState.installApkTrigger) {
        val apkFile = uiState.installApkTrigger ?: return@LaunchedEffect
        onInstallLaunched()
        if (context.packageManager.canRequestPackageInstalls()) {
            launchApkInstaller(context, apkFile)?.let(onInstallError)
        } else {
            pendingPermissionApkPath = apkFile.absolutePath
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            )
            try {
                unknownSourcesLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                pendingPermissionApkPath = null
                onInstallError(e.message ?: e::class.java.simpleName)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { padding ->
        RefreshableContent(
            isRefreshing = isRefreshing,
            refreshProgress = refreshProgress,
            onRefresh = onRefreshAll,
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
            Text(text = stringResource(R.string.add_podcast), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = rssUrl,
                onValueChange = { rssUrl = it },
                label = { Text(stringResource(R.string.add_podcast_rss)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onAddPodcast(rssUrl)
                    rssUrl = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = rssUrl.isNotBlank()
            ) {
                Text(stringResource(R.string.add_podcast))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            val isImporting = uiState.importWorkInfo?.state == WorkInfo.State.RUNNING
            val progress = uiState.importWorkInfo?.progress?.getInt(Constants.WORK_KEY_PROGRESS, 0) ?: 0
            val total = uiState.importWorkInfo?.progress?.getInt(Constants.WORK_KEY_TOTAL, 0) ?: 0

            Text(text = stringResource(R.string.opml_import_export), style = MaterialTheme.typography.titleLarge)
            
            if (isImporting) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.importing_podcasts, progress, total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { opmlImportLauncher.launch(Constants.OPML_MIME_TYPES) },
                    modifier = Modifier.weight(1f),
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.import_btn))
                }
                Spacer(modifier = Modifier.width(8.dp))
                val opmlFilename = stringResource(R.string.default_opml_filename)
                Button(
                    onClick = { opmlExportLauncher.launch(opmlFilename) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export_btn))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))



            Text(text = stringResource(R.string.import_local_audio_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { localAudioLauncher.launch(Constants.AUDIO_MIME_TYPES) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_local_audio_btn))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(text = stringResource(R.string.debugging_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.logNote,
                onValueChange = onLogNoteChanged,
                label = { Text(stringResource(R.string.debugging_explain_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        isLogNoteFocused = focusState.isFocused
                    },
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onSaveLogNote,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.logNote.isNotBlank()
                ) {
                    Text(stringResource(R.string.log_event_btn))
                }
                Spacer(modifier = Modifier.width(8.dp))
                val logsFilename = stringResource(R.string.default_logs_filename)
                Button(
                    onClick = { logExportLauncher.launch(logsFilename) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.download_logs_btn))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            AppUpdatesSection(
                updateState = uiState.appUpdateState,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onCancelUpdateDownload = onCancelUpdateDownload,
                onInstallUpdate = onInstallUpdate
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(
                    R.string.app_version,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.BUILD_DATE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            }
        }
    }
}

@Composable
private fun AppUpdatesSection(
    updateState: AppUpdateState,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.app_updates_title),
        style = MaterialTheme.typography.titleLarge
    )
    Spacer(modifier = Modifier.height(8.dp))

    when (updateState) {
        is AppUpdateState.Idle,
        is AppUpdateState.Checking,
        is AppUpdateState.UpToDate -> {
            val isChecking = updateState is AppUpdateState.Checking
            Button(
                onClick = onCheckForUpdates,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChecking
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.checking_for_updates))
                } else {
                    Text(stringResource(R.string.check_for_updates_btn))
                }
            }
            if (updateState is AppUpdateState.UpToDate) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.app_up_to_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        is AppUpdateState.UpdateAvailable -> {
            val release = updateState.release
            val formattedSize = remember(release.apkSizeBytes) {
                if (release.apkSizeBytes > 0L) Formatter.formatShortFileSize(context, release.apkSizeBytes) else null
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.update_available_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.update_available_meta,
                            release.commitHash,
                            release.versionCode,
                            release.buildTime
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (release.commitMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = release.commitMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onDownloadUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (formattedSize != null) {
                                stringResource(R.string.download_and_install_btn, formattedSize)
                            } else {
                                stringResource(R.string.download_and_install_btn_no_size)
                            }
                        )
                    }
                    TextButton(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.check_again_btn))
                    }
                }
            }
        }

        is AppUpdateState.Downloading -> {
            val progress = updateState.progress
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (progress != null) {
                            val percent = (progress * 100f).roundToInt().coerceIn(0, 100)
                            stringResource(R.string.downloading_update_progress, percent)
                        } else {
                            stringResource(R.string.downloading_update_indeterminate)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onCancelUpdateDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cancel_btn))
                    }
                }
            }
        }

        is AppUpdateState.ReadyToInstall -> {
            val release = updateState.release
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.update_available_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.update_available_meta,
                            release.commitHash,
                            release.versionCode,
                            release.buildTime
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onInstallUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.install_update_btn))
                    }
                    TextButton(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.check_again_btn))
                    }
                }
            }
        }
    }
}

private fun launchApkInstaller(context: Context, apkFile: File): String? {
    return try {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, Constants.MIME_TYPE_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        null
    } catch (e: Exception) {
        e.message ?: e::class.java.simpleName
    }
}
