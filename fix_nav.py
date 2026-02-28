with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "r") as f:
    content = f.read()

replacement = """                            onClick = {
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
                            }"""

content = content.replace("""                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }""", replacement)

with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "w") as f:
    f.write(content)
