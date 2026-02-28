with open("build.gradle.kts", "r") as f:
    content = f.read()
if "com.github.ben-manes.versions" not in content:
    content = content.replace("plugins {", "plugins {\n    id(\"com.github.ben-manes.versions\") version \"0.51.0\"")
    with open("build.gradle.kts", "w") as f:
        f.write(content)
