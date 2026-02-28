with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("            var expanded by remember { mutableStateOf(false) }\n\n            PodcastItem(\n                podcast = podcast,\n                onClick = { onPodcastClick(podcast.feedUrl) },\n                trailingContent = {", "            var expanded by remember { mutableStateOf(false) }\n            val handlePodcastClick = remember(podcast.feedUrl) { { onPodcastClick(podcast.feedUrl) } }\n\n            PodcastItem(\n                podcast = podcast,\n                onClick = handlePodcastClick,\n                trailingContent = {")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "w") as f:
    f.write(content)
