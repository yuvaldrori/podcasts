with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace("EpisodeItem(\n                    episode = episode,\n                    modifier = Modifier.clickable { onEpisodeClick(episode.id) },", "val clickHandler = remember(episode.id) { { onEpisodeClick(episode.id) } }\n                EpisodeItem(\n                    episode = episode,\n                    modifier = Modifier.clickable(onClick = clickHandler),")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/PodcastDetailScreen.kt", "w") as f:
    f.write(content)
