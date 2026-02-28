with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "r") as f:
    content = f.read()

content = content.replace("""                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unsubscribe") },
                                onClick = {
                                    expanded = false
                                    viewModel.unsubscribePodcast(podcast.feedUrl)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }""", """                        val onExpandClick = remember(podcast.feedUrl) { { expanded = true } }
                        val onDismissRequest = remember(podcast.feedUrl) { { expanded = false } }
                        val onUnsubscribeClick = remember(podcast.feedUrl) { 
                            { 
                                expanded = false
                                viewModel.unsubscribePodcast(podcast.feedUrl) 
                            } 
                        }
                        
                        IconButton(onClick = onExpandClick) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(24.dp))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = onDismissRequest
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unsubscribe") },
                                onClick = onUnsubscribeClick,
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }""")

with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "w") as f:
    f.write(content)
