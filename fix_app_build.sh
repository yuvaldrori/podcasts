#!/bin/bash
sed -i '/dependencies {/a \    implementation("androidx.tracing:tracing-ktx:1.2.0")' app/build.gradle.kts
sed -i '/buildTypes {/a \        create("benchmark") {\n            initWith(buildTypes.getByName("release"))\n            matchingFallbacks += listOf("release")\n            signingConfig = signingConfigs.getByName("debug")\n            // isProfileable is configured via manifest in modern AGP usually, but let us try property\n            isProfileable = true\n        }' app/build.gradle.kts
