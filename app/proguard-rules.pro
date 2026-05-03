# ---------------------------------------------------------------------------
# Project-Specific R8/Proguard Rules
# ---------------------------------------------------------------------------

# --- Kotlin Serialization ---
# Keep serializable classes and their companion objects for Navigation
-keepclassmembers class com.yuval.podcasts.ui.navigation.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.yuval.podcasts.ui.navigation.** { *; }

# --- Room ---
# Room handles its own library rules, but we keep our entities safe if needed for reflection-based migrations or debugging
-keep @androidx.room.Entity class com.yuval.podcasts.data.db.entity.** { *; }
-keep @androidx.room.Dao interface com.yuval.podcasts.data.db.dao.** { *; }

# --- Hilt / Dagger ---
# Most Hilt rules are bundled with the library, but we keep entry points safe
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Activity
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Service
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.content.BroadcastReceiver

# --- Media3 / Cast ---
# Project-specific Cast integration
-keep class com.yuval.podcasts.media.cast.CastOptionsProvider { *; }

