with open("app/build.gradle.kts", "r") as f:
    content = f.read()

replacement = """    buildTypes {
        debug {
            // Optimize debug build speed by disabling PNG crunching
            isCrunchPngs = false
        }
        release {"""

content = content.replace("    buildTypes {\n        release {", replacement)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
