with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.removeFromQueue(episodeWithPodcast.episode.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        ),""", """                    val onDismiss = remember(episodeWithPodcast.episode.id) {
                        { value: SwipeToDismissBoxValue ->
                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.removeFromQueue(episodeWithPodcast.episode.id)
                                true
                            } else {
                                false
                            }
                        }
                    }
                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = onDismiss
                        ),""")

content = content.replace("""                                    IconButton(onClick = { viewModel.play(episodeWithPodcast.episode) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }""", """                                    val onPlayClick = remember(episodeWithPodcast.episode.id) { { viewModel.play(episodeWithPodcast.episode) } }
                                    IconButton(onClick = onPlayClick) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
