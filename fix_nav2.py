with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "r") as f:
    content = f.read()

replacement = """                            val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                                             (screen == Screen.Subscriptions && currentDestination?.route?.startsWith("podcast_detail") == true) ||
                                             (screen == Screen.Queue && currentDestination?.route?.startsWith("episode_detail") == true)

                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.title) },
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        navController.popBackStack(screen.route, false)
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }"""

content = content.replace("""                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.title) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                                        navController.popBackStack(screen.route, false)
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                }""", replacement)

with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "w") as f:
    f.write(content)
