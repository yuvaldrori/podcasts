import re

with open('app/src/test/java/com/yuval/podcasts/data/repository/PodcastRepositoryRefreshTest.kt', 'r') as f:
    content = f.read()

# Replace runTest virtual time approach with real time approach to measure true concurrency
content = content.replace("import kotlinx.coroutines.test.currentTime", "")
content = content.replace("val startTime = currentTime", "val startTime = System.currentTimeMillis()")
content = content.replace("val endTime = currentTime", "val endTime = System.currentTimeMillis()")

with open('app/src/test/java/com/yuval/podcasts/data/repository/PodcastRepositoryRefreshTest.kt', 'w') as f:
    f.write(content)
