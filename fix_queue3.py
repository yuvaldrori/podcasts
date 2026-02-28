with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "r") as f:
    content = f.read()

content = content.replace("    LaunchedEffect(dbQueue) { queue = dbQueue }\n", "")

insert_str = """
    LaunchedEffect(dbQueue, state.draggingItemKey) { 
        if (state.draggingItemKey == null) {
            queue = dbQueue 
        }
    }
"""

content = content.replace("    Column(modifier = Modifier.fillMaxSize()) {", insert_str + "\n    Column(modifier = Modifier.fillMaxSize()) {")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt", "w") as f:
    f.write(content)
