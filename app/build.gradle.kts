import java.util.Date
import java.text.SimpleDateFormat
import java.util.Properties
import java.io.FileInputStream

val versionProps = Properties().apply {
    load(FileInputStream(rootProject.file("version.properties")))
}

val verMajor = versionProps.getProperty("VERSION_MAJOR").toInt()
val verMinor = versionProps.getProperty("VERSION_MINOR").toInt()
val verPatch = versionProps.getProperty("VERSION_PATCH").toInt()
val verCode = versionProps.getProperty("VERSION_CODE").toInt()

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.yuval.podcasts"
    
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.yuval.podcasts"
        minSdk = 36
        targetSdk = 36
        versionCode = verCode
        versionName = "$verMajor.$verMinor.$verPatch"

        buildConfigField("String", "BUILD_DATE", "\"${SimpleDateFormat("yyyy.MM.dd-HH:mm").format(Date())}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        }
        debug {
            // Optimize debug build speed by disabling PNG crunching
            isCrunchPngs = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        checkReleaseBuilds = false
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
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
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.navigation.compose)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
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

    // Networking
    
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
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.media3.test.utils)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("incrementVersionCode") {
    val propsFile = rootProject.file("version.properties")
    inputs.file(propsFile)
    outputs.file(propsFile)
    
    doLast {
        val props = Properties()
        FileInputStream(propsFile).use { props.load(it) }
        val code = props.getProperty("VERSION_CODE").toInt()
        props.setProperty("VERSION_CODE", (code + 1).toString())
        propsFile.writer().use { props.store(it, "Auto-incremented Version Code") }
        println("Version Code incremented to ${code + 1}")
    }
}

// Automatically increment version code for release builds
tasks.whenTaskAdded {
    if (name == "assembleRelease" || name == "bundleRelease") {
        dependsOn("incrementVersionCode")
    }
}
