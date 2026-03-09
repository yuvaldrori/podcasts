with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "r") as f:
    content = f.read()

# remove the fix (LaunchedEffect and SnackbarHost)
content = content.replace(
'''    val errorMessage = (uiState as? FeedsUiState.Success)?.errorMessage
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },''',
'''    Scaffold('''
)

with open("app/src/main/java/com/yuval/podcasts/ui/screens/NewEpisodesScreen.kt", "w") as f:
    f.write(content)
