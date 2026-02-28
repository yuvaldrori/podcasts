with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "r") as f:
    content = f.read()

replacement = """        NavHost(
            navController = navController,
            startDestination = Screen.Queue.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(0)) },
            popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(0)) }
        ) {"""

content = content.replace("""        NavHost(
            navController = navController,
            startDestination = Screen.Queue.route,
            modifier = Modifier.padding(innerPadding)
        ) {""", replacement)

with open("app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt", "w") as f:
    f.write(content)
