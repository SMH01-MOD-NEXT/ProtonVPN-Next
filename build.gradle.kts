// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Android plugins - Updated to 8.8.0 for Gradle 8.10+ / 9.x and compileSdk 35 compatibility
    id("com.android.application") version "8.8.0" apply false
    id("com.android.library") version "8.8.0" apply false

    // Kotlin plugins
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false

    // KSP & Hilt
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false

    // Room
    id("androidx.room") version "2.7.2" apply false
}

// Global cleanup task
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}