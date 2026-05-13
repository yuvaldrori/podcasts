# ---------------------------------------------------------------------------
# Project-Specific R8/Proguard Rules
# ---------------------------------------------------------------------------

# --- Kotlin Serialization & Navigation ---
# Navigation 2.8+ uses reflection to instantiate routes from @Serializable classes.
# We keep the classes and their members to ensure type-safe routing works after minification.
-keep @kotlinx.serialization.Serializable class com.yuval.podcasts.ui.navigation.** { *; }

# --- Media3 / Cast ---
# The Cast SDK instantiates the options provider via reflection using its class name.
-keep class com.yuval.podcasts.media.cast.CastOptionsProvider

