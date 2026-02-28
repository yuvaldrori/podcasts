with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                    trailingContent = {
                        IconButton(onClick = { viewModel.addToQueue(episode) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                        }
                    }""", """                    trailingContent = {
                        val onAddClick = remember(episode.id) { { viewModel.addToQueue(episode) } }
                        IconButton(onClick = onAddClick) {
                            Icon(Icons.Default.Add, contentDescription = "Add to Queue")
                        }
                    }""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "w") as f:
    f.write(content)
