with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val episodes by viewModel.getEpisodesForPodcast(feedUrl).collectAsStateWithLifecycle(emptyList())",
    "val episodesFlow = remember(feedUrl) { viewModel.getEpisodesForPodcast(feedUrl) }\n    val episodes by episodesFlow.collectAsStateWithLifecycle(emptyList())"
)

with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "w") as f:
    f.write(content)
