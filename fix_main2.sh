sed -i 's/currentDestination?.hierarchy?.any { it.route == screen.route } == true/currentDestination?.hierarchy?.any { it.route == screen.route::class.qualifiedName } == true/g' app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt
sed -i 's/navController.popBackStack(screen.route, false)/navController.popBackStack(screen.route, false)/g' app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt
sed -i 's/navController.navigate(screen.route) {/navController.navigate(screen.route) {/g' app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt
