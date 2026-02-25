package com.yuval.podcasts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuval.podcasts.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var rssUrl by remember { mutableStateOf("") }

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
    }
}