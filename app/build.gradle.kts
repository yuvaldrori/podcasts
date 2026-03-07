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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/tmp/android.jks")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("androidx.tracing:tracing-ktx:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Coil
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Media3
    val media3Version = "1.9.2"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-cast:$media3Version")
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")

    // Hilt
    val hiltVersion = "2.59.2"
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Networking
    
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("androidx.test:core-ktx:1.7.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

dependencies {
    implementation("androidx.tracing:tracing-ktx:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
}
