package com.yuval.podcasts.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var rssUrl by remember { mutableStateOf("") }
    val context = LocalContext.current

    val opmlImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.importOpml(stream)
                }
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

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    viewModel.backupDatabase(stream)
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    viewModel.restoreDatabase(stream)
                }
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
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { opmlImportLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        Text(text = "Database Backup", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { backupLauncher.launch("podcasts_backup.db") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Backup")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Restore")
            }
        }
    }
}