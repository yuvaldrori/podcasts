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
                viewModel.importOpml(context, it)
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
    }
}
