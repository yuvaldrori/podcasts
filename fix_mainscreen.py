with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "r") as f:
    content = f.read()

content = content.replace("PodcastDetailScreen(\n                    feedUrl = java.net.URLDecoder.decode(feedUrl, \"UTF-8\"),\n                    onEpisodeClick = { episodeId -> ", "PodcastDetailScreen(\n                    feedUrl = java.net.URLDecoder.decode(feedUrl, \"UTF-8\"),\n                    onBack = { navController.popBackStack() },\n                    onEpisodeClick = { episodeId -> ")

with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "w") as f:
    f.write(content)
