# ---------------------------------------------------------------------------
# Project-Specific R8/Proguard Rules
# ---------------------------------------------------------------------------

# --- Kotlin Serialization ---
# Keep serializable classes and their companion objects
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.json.** { *; }
-keepclassmembers class com.yuval.podcasts.ui.navigation.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.yuval.podcasts.ui.navigation.** { *; }

# --- Room ---
# Room uses reflection to instantiate DAOs and Entities
-keep class com.yuval.podcasts.data.db.entity.** { *; }
-keep class com.yuval.podcasts.data.db.dao.** { *; }

# --- Hilt / Dagger ---
# Most Hilt rules are bundled with the library, but we keep entry points safe
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Activity
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Service
-keep @dagger.hilt.android.AndroidEntryPoint class * extends android.content.BroadcastReceiver

# --- Media3 / Cast ---
# Keep Media3 items used in reflection and Cast integration
-keep class androidx.media3.common.MediaItem { *; }
-keep class androidx.media3.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-keep class com.yuval.podcasts.media.cast.CastOptionsProvider { *; }

# --- OkHttp ---
# OkHttp uses reflection for certain features
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- App Infrastructure ---
# Keep workers for WorkManager reflection
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
