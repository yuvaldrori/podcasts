with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("            modifier = Modifier\n                .weight(1f)\n                .reorderable(state)\n                .detectReorder(state),", "            modifier = Modifier\n                .weight(1f)\n                .reorderable(state)\n                .detectReorderAfterLongPress(state),")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
