with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("modifier = Modifier.padding(start = 8.dp).detectReorder(state)", "modifier = Modifier.padding(start = 8.dp)")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
