#!/bin/bash

# Remove onShare lambda block entirely from both usages in the file
sed -i '/onShare = { episode ->/,/}/d' app/src/androidTest/java/com/yuval/podcasts/ui/screens/EpisodeDetailScreenShareTest.kt
sed -i 's/onAddToQueue = {},/onAddToQueue = {}/g' app/src/androidTest/java/com/yuval/podcasts/ui/screens/EpisodeDetailScreenShareTest.kt

