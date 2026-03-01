with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", "@OptIn(ExperimentalMaterial3Api::class)\n") # Just ensuring it's there
# wait, PullToRefreshContainer requires OptIn.

