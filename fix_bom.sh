#!/bin/bash
sed -i 's/val composeBom = platform("androidx.compose:compose-bom:2024.11.00")/val composeBom = platform("androidx.compose:compose-bom:2024.11.00")\n    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")/g' app/build.gradle.kts
