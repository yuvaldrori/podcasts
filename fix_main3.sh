sed -i 's/navController.popBackStack(screen.route, false)/navController.popBackStack(screen.route, inclusive = false)/g' app/src/main/java/com/yuval/podcasts/ui/MainScreen.kt
