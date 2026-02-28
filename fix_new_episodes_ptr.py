with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.material3.pulltorefresh.PullToRefreshContainer", "")
content = content.replace("import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState", "import androidx.compose.material3.pulltorefresh.PullToRefreshBox\nimport androidx.compose.material3.pulltorefresh.rememberPullToRefreshState")
content = content.replace(".nestedScroll(pullToRefreshState.nestedScrollConnection)", "")
content = content.replace("""    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }""", "")

content = content.replace("""        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                
        ) {""", """        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {""")

content = content.replace("""            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )""", "")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
