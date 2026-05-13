# R8 Configuration Analysis

## Configuration Audit
- **AGP Version**: 9.2.1 (Upgraded from 9.2.0. Meets the "AGP 9.0+" recommendation for improved optimizations).
- **MinifyEnabled**: `true` for release builds.
- **ShrinkResources**: `true` for release builds.

## Proguard Rules Analysis

### Redundant Rules
The following rules are redundant because they are already covered by library consumer rules or AAPT2:

1.  **Hilt / Android Components**:
    ```proguard
    -keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Activity
    -keep @dagger.hilt.android.AndroidEntryPoint class * extends android.app.Service
    -keep @dagger.hilt.android.AndroidEntryPoint class * extends android.content.BroadcastReceiver
    ```
    **Action**: Remove. AAPT2 automatically keeps components declared in `AndroidManifest.xml`. Hilt's own rules handle entry points.

2.  **Room DAOs and Entities**:
    ```proguard
    -keep @androidx.room.Entity class com.yuval.podcasts.data.db.entity.** { *; }
    -keep @androidx.room.Dao interface com.yuval.podcasts.data.db.dao.** { *; }
    ```
    **Action**: Remove. Room bundles its own consumer keep rules.

### Impact Analysis & Subsuming Rules

1.  **Navigation Routes (Package-Wide Wildcard)**:
    ```proguard
    -keep @kotlinx.serialization.Serializable class com.yuval.podcasts.ui.navigation.** { *; }
    ```
    **Action**: Refine. This rule prevents optimization for all members in the navigation package. Since Navigation uses reflection on the `@Serializable` class, it should be narrowed.
    **Recommendation**: Refine to only keep necessary members or use `-keep,allowobfuscation` if only serialization is needed. However, Navigation 2.8+ often requires the full class for type-safe routing.

2.  **Cast Options Provider**:
    ```proguard
    -keep class com.yuval.podcasts.media.cast.CastOptionsProvider { *; }
    ```
    **Action**: Keep but refine. This is Hierarchy #3 (Keep both class and members). 
    **Recommendation**: Refine to only keep the class name or specific methods if only the constructor is needed by the Cast SDK via reflection.

## Recommendations
1.  **Remove** redundant rules for Hilt and Room.
2.  **Refine** Navigation rules to avoid package-wide wildcards.
3.  **Validate** by running a release build and testing Navigation and Database operations.
