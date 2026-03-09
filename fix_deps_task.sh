sed -i '/tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>/,$d' build.gradle.kts
cat << 'INNER' >> build.gradle.kts
tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        val lowerCaseVersion = candidate.version.lowercase()
        val isStable = "^[0-9,.v-]+(-r)?$".toRegex().matches(lowerCaseVersion)
        val isSnapshot = lowerCaseVersion.contains("snapshot")
        val isAlpha = lowerCaseVersion.contains("alpha")
        val isBeta = lowerCaseVersion.contains("beta")
        val isRc = lowerCaseVersion.contains("rc")
        val isM = lowerCaseVersion.matches(".*-m\\d+.*".toRegex())
        
        val isNonStable = isSnapshot || isAlpha || isBeta || isRc || isM
        
        // Return true to reject the version if it's considered non-stable
        // However, if the current version is already non-stable, allow upgrading to another non-stable version
        if (isNonStable) {
            val currentLowerCaseVersion = currentVersion.lowercase()
            val currentIsStable = "^[0-9,.v-]+(-r)?$".toRegex().matches(currentLowerCaseVersion)
            val currentIsNonStable = currentLowerCaseVersion.contains("snapshot") || 
                                     currentLowerCaseVersion.contains("alpha") || 
                                     currentLowerCaseVersion.contains("beta") || 
                                     currentLowerCaseVersion.contains("rc") ||
                                     currentLowerCaseVersion.matches(".*-m\\d+.*".toRegex())
                                     
            if (currentIsNonStable) {
                // If we are already on a non-stable version, allow updates to newer non-stable versions
                false
            } else {
                // If we are on a stable version, reject non-stable updates
                true
            }
        } else {
            false
        }
    }

    doFirst {
        // Resolve all configurations explicitly to prevent concurrent modification later
        project.allprojects.forEach { p ->
            p.configurations.toList().forEach { conf ->
                if (conf.isCanBeResolved) {
                    try { conf.resolve() } catch (e: Exception) {}
                }
            }
            p.buildscript.configurations.toList().forEach { conf ->
                if (conf.isCanBeResolved) {
                    try { conf.resolve() } catch (e: Exception) {}
                }
            }
        }
    }
}
INNER
