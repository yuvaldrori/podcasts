with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "r") as f:
    content = f.read()

content = content.replace('Icon(Icons.Default.MoreVert, contentDescription = "Options")', 'Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(24.dp))')

with open("app/src/main/java/com/yuval/podcasts/ui/screens/SubscriptionsScreen.kt", "w") as f:
    f.write(content)
