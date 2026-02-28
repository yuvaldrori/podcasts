with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.material.icons.filled.Add", "import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.automirrored.filled.ArrowBack")
content = content.replace("fun PodcastDetailScreen(\n    feedUrl: String,\n    onEpisodeClick: (String) -> Unit,\n    viewModel: FeedsViewModel = hiltViewModel()\n) {", "fun PodcastDetailScreen(\n    feedUrl: String,\n    onBack: () -> Unit,\n    onEpisodeClick: (String) -> Unit,\n    viewModel: FeedsViewModel = hiltViewModel()\n) {")
content = content.replace("TopAppBar(title = { Text(\"Episodes\") })", """TopAppBar(
                title = { Text("Episodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "w") as f:
    f.write(content)
