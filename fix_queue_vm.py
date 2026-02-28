import re
with open("app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt", "r") as f:
    content = f.read()

content = re.sub(r"override fun onCleared\(\) \{[\s\n]*super\.onCleared\(\)[\s\n]*playerManager\.release\(\)[\s\n]*\}", "", content)

with open("app/src/main/java/com/yuval/podcasts/ui/viewmodel/QueueViewModel.kt", "w") as f:
    f.write(content)
