import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val gitCommitCount = providers.gradleProperty("versionCodeOverride")
    .map { it.toInt() }
    .orElse(
        providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }
    ).get()

val gitCommitHash = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }.get()

val releaseBuildDate = SimpleDateFormat("yyyy.MM.dd-HH:mm 'UTC'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("appfunctions:aggregateAppFunctions", "true")
}

android {
    namespace = "com.yuval.podcasts"
    
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.yuval.podcasts"
        minSdk = 37
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = gitCommitHash

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/android.jks")
            storePassword = System.getenv("JKS_PASSWORD") ?: ""
            keyAlias = System.getenv("JKS_ALIAS") ?: ""
            keyPassword = System.getenv("JKS_PASSWORD") ?: ""
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            // isProfileable is configured via manifest in modern AGP usually, but let us try property
            isProfileable = true
            buildConfigField("String", "BUILD_DATE", "\"BENCHMARK\"")
        }
        debug {
            // Optimize debug build speed by disabling PNG crunching
            isCrunchPngs = false
            buildConfigField("String", "BUILD_DATE", "\"DEBUG\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BUILD_DATE", "\"$releaseBuildDate\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        disable += listOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion")
    }

    sourceSets {
        getByName("test") {
            assets.directories.add("$projectDir/schemas")
        }
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += setOf("**/libandroidx.graphics.path.so", "**/libbenchmarkNative.so", "**/libtracing_perfetto.so")
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.kotlinx.coroutines.guava)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(libs.kotlinx.collections.immutable)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.navigation.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.material.color.utilities)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // AppFunctions
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.work.testing)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.media3.test.utils)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
