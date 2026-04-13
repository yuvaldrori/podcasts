package com.yuval.podcasts.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuval.podcasts.R
import com.yuval.podcasts.ui.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAddPodcast: (String) -> Unit,
    onImportOpml: (Uri) -> Unit,
    onExportOpml: (android.content.Context, Uri) -> Unit,
    onExportHistory: (android.content.Context, Uri) -> Unit,
    onImportHistory: (Uri) -> Unit,
    onToggleSkipSilence: (Boolean) -> Unit,
    onImportLocalAudio: (Uri) -> Unit,
    onClearError: () -> Unit
) {
    var rssUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage)
            onClearError()
        }
    }

    val opmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpml(it) } }
    )

    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml"),
        onResult = { uri -> uri?.let { onExportOpml(context, it) } }
    )

    val historyExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let { onExportHistory(context, it) } }
    )

    val historyImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportHistory(it) } }
    )

    val localAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { onImportLocalAudio(it) } }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
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

            val isImporting = uiState.importWorkInfo?.state == androidx.work.WorkInfo.State.RUNNING
            val progress = uiState.importWorkInfo?.progress?.getInt("PROGRESS", 0) ?: 0
            val total = uiState.importWorkInfo?.progress?.getInt("TOTAL", 0) ?: 0

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
                    onClick = { opmlImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                    enabled = !isImporting
                ) {
                    Text(stringResource(R.string.import_btn))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { opmlExportLauncher.launch("podcasts.opml") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export_btn))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(text = stringResource(R.string.history_import_export), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { historyImportLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.import_history_btn))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { historyExportLauncher.launch("podcasts_history.json") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.export_history_btn))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.smart_silence_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.smart_silence_desc), 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.skipSilenceEnabled,
                    onCheckedChange = onToggleSkipSilence
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(text = stringResource(R.string.import_local_audio_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { localAudioLauncher.launch("audio/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_local_audio_btn))
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(
                    R.string.app_version, 
                    com.yuval.podcasts.BuildConfig.VERSION_NAME, 
                    com.yuval.podcasts.BuildConfig.BUILD_DATE
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
