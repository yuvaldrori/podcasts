package com.yuval.podcasts.ui.screens
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yuval.podcasts.ui.viewmodel.SettingsViewModel

import androidx.work.WorkInfo

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var rssUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val importWorkInfo by viewModel.importWorkInfo.collectAsStateWithLifecycle()

    val opmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                viewModel.importOpml(it)
            }
        }
    )

    val opmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/x-opml"),
        onResult = { uri ->
            uri?.let {
                viewModel.exportOpml(context, it)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Add Podcast RSS", style = MaterialTheme.typography.titleLarge)
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
                    viewModel.addPodcast(rssUrl)
                    rssUrl = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Podcast")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = "OPML Import/Export", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Show progress UI if an import is actively running
        val isImporting = importWorkInfo?.state == WorkInfo.State.RUNNING || importWorkInfo?.state == WorkInfo.State.ENQUEUED
        if (isImporting) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val progress = importWorkInfo?.progress?.getInt("PROGRESS", 0) ?: 0
                    val total = importWorkInfo?.progress?.getInt("TOTAL", 1) ?: 1
                    
                    Text(
                        text = "Importing podcasts: $progress of $total",
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
                Text("Import")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { opmlExportLauncher.launch("podcasts.opml") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Export")
            }
        }
    }
}
