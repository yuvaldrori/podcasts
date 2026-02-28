with open("app/src/main/java/com/yuval/podcasts/ui/components/EpisodeItem.kt", "r") as f:
    content = f.read()

content = content.replace("private val dateFormat = SimpleDateFormat(\"MMM dd, yyyy\", Locale.getDefault())", "private val dateFormat = java.lang.ThreadLocal.withInitial { SimpleDateFormat(\"MMM dd, yyyy\", Locale.getDefault()) }")
content = content.replace("return dateFormat.format(Date(timestamp))", "return dateFormat.get()?.format(Date(timestamp)) ?: \"\"")

with open("app/src/main/java/com/yuval/podcasts/ui/components/EpisodeItem.kt", "w") as f:
    f.write(content)
