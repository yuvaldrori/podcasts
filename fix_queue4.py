with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("viewModel.reorderItem(startIndex, endIndex)", "viewModel.reorderQueue(queue.map { it.episode.id })")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
