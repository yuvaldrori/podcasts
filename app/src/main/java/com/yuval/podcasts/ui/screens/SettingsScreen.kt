package com.yuval.podcasts.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.yuval.podcasts.ui.viewmodel.SettingsUiState

import androidx.compose.ui.res.stringResource
import com.yuval.podcasts.R

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAddPodcast: (String) -> Unit,
    onImportOpml: (Uri) -> Unit,
    onExportOpml: (android.content.Context, Uri) -> Unit,
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

    val localAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { onImportLocalAudio(it) } }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.add_podcast_rss), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = rssUrl,
                onValueChange = { rssUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://example.com/rss") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (rssUrl.isNotBlank()) {
                        onAddPodcast(rssUrl)
                        rssUrl = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.add_podcast))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(text = stringResource(R.string.opml_import_export), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            val isImporting = uiState.importWorkInfo?.state == WorkInfo.State.RUNNING || uiState.importWorkInfo?.state == WorkInfo.State.ENQUEUED
            if (isImporting) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val progress = uiState.importWorkInfo?.progress?.getInt("PROGRESS", 0) ?: 0
                        val total = uiState.importWorkInfo?.progress?.getInt("TOTAL", 1) ?: 1
                        
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

            Text(text = stringResource(R.string.import_local_audio_title), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { localAudioLauncher.launch("audio/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.import_local_audio_btn))
            }
        }
    }
}
