sed -i 's/\.detectReorderAfterLongPress(state)//g' app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt
sed -i 's/modifier = Modifier.padding(start = 8.dp)/modifier = Modifier.padding(start = 8.dp).detectReorder(state)/g' app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt
sed -i 's/import org.burnoutcrew.reorderable.detectReorderAfterLongPress/import org.burnoutcrew.reorderable.detectReorder/g' app/src/main/java/com/yuval/podcasts/ui/screens/QueueScreen.kt
