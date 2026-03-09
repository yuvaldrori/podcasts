sed -i 's/feedsUiState.podcasts/(feedsUiState as? FeedsUiState.Success)?.podcasts ?: emptyList()/g' app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt
