with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("EpisodeItem(\n                                episode = episodeWithPodcast.episode,\n                                modifier = Modifier.clickable { onEpisodeClick(episodeWithPodcast.episode.id) },", "val clickHandler = remember(episodeWithPodcast.episode.id) { { onEpisodeClick(episodeWithPodcast.episode.id) } }\n                            EpisodeItem(\n                                episode = episodeWithPodcast.episode,\n                                modifier = Modifier.clickable(onClick = clickHandler),")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
