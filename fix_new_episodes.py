with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    viewModel.dismissEpisode(episodeWithPodcast.episode)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    viewModel.addToQueue(episodeWithPodcast.episode)
                                    true
                                }
                                else -> false
                            }
                        }
                    )""", """                    val onDismiss = remember(episodeWithPodcast.episode.id) {
                        { value: SwipeToDismissBoxValue ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> {
                                    viewModel.dismissEpisode(episodeWithPodcast.episode)
                                    true
                                }
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    viewModel.addToQueue(episodeWithPodcast.episode)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = onDismiss
                    )""")

content = content.replace("""                                    Row {
                                        IconButton(onClick = { viewModel.dismissEpisode(episodeWithPodcast.episode) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                                        }
                                        IconButton(onClick = { viewModel.addToQueue(episodeWithPodcast.episode) }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                                        }
                                    }""", """                                    Row {
                                        val onDismissClick = remember(episodeWithPodcast.episode.id) { { viewModel.dismissEpisode(episodeWithPodcast.episode) } }
                                        val onAddClick = remember(episodeWithPodcast.episode.id) { { viewModel.addToQueue(episodeWithPodcast.episode) } }
                                        IconButton(onClick = onDismissClick) {
                                            Icon(Icons.Default.Delete, contentDescription = "Dismiss")
                                        }
                                        IconButton(onClick = onAddClick) {
                                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                                        }
                                    }""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
