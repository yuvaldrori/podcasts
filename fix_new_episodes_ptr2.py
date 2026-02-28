with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                    IconButton(onClick = { 
                        coroutineScope.launch {
                            pullToRefreshState.startRefresh()
                        }
                     }) {""", """                    IconButton(onClick = { viewModel.refreshAll() }) {""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
