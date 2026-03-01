with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.material3.pulltorefresh.PullToRefreshBox\nimport androidx.compose.material3.pulltorefresh.rememberPullToRefreshState", "import androidx.compose.material3.pulltorefresh.PullToRefreshContainer\nimport androidx.compose.material3.pulltorefresh.rememberPullToRefreshState\nimport androidx.compose.ui.input.nestedscroll.nestedScroll")

content = content.replace("""        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {""", """        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {""")

content = content.replace("""    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }""", """    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }""")

content = content.replace("""            }
        }""", """            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
