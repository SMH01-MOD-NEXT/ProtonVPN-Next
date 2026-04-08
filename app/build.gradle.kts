/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import java.util.concurrent.TimeUnit

// Helper function to execute Git commands in the terminal
fun getGitOutput(command: String, workingDir: java.io.File): String {
    return try {
        val process = ProcessBuilder(command.split(" "))
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        process.waitFor(10, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        ""
    }
}

// Dynamically generate version name based on the latest Git tag
fun getDynamicVersionName(workingDir: java.io.File): String {
    val gitVersion = getGitOutput("git describe --tags --always", workingDir)
    // Fallback to "12.0.0" if Git is not available (e.g., downloaded as a ZIP)
    return gitVersion.ifEmpty { "12.0.0" }
}

// Dynamically generate version code using total commit count to ensure it strictly increases.
// Using total count instead of "since last tag" prevents resets when a new tag is created.
fun getDynamicVersionCode(workingDir: java.io.File): Int {
    // We use 'HEAD' to count all commits in the current branch's history.
    // In CI, ensure a full clone (depth: 0) is performed for this to work.
    val commitCount = getGitOutput("git rev-list --count HEAD", workingDir).toIntOrNull() ?: 0
    // Base version code prevents the number from ever dropping below your current state
    val baseVersionCode = 605159512
    return baseVersionCode + commitCount
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.sentry)
}

android {
    namespace = "ru.protonmod.next"
    compileSdk = 37

    // Force AGP to use a specific NDK version instead of the default one
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "ru.protonmod.next"
        minSdk = 29
        targetSdk = 37
        versionCode = getDynamicVersionCode(rootDir)
        versionName = getDynamicVersionName(rootDir)

        // Support 64-bit architectures only
        // 32-bit devices (armeabi-v7a, armeabi) are not supported as the VPN engine
        // (AmneziaWG via go-vpn-lib) and its native libraries are compiled for 64-bit only
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions.add("channel")
    productFlavors {
        create("stable") {
            dimension = "channel"
            buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
        }
        create("nightly") {
            dimension = "channel"
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            buildConfigField("String", "UPDATE_CHANNEL", "\"nightly\"")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            val keyFile = System.getenv("SIGNING_KEY_FILE") ?: ""
            if (keyFile.isNotEmpty()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                // Fallback to debug for local builds without env vars
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            buildConfigField("boolean", "ALLOW_LOGCAT", "true")
            // Use release signing config in CI environments to ensure consistent signatures
            signingConfig = if (System.getenv("SIGNING_KEY_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            packaging {
                jniLibs {
                    keepDebugSymbols.addAll(listOf(
                        "**/libam-go.so",
                        "**/libam-quick.so",
                        "**/libam.so",
                        "**/libandroidx.graphics.path.so",
                        "**/libdatastore_shared_counter.so",
                        "**/libgojni.so",
                        "**/libhev-socks5-tunnel.so",
                        "**/libsentry-android.so",
                        "**/libsentry.so"
                    ))
                }
            }
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ALLOW_LOGCAT", "false")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    configurations.all {
        exclude(group = "me.proton.crypto", module = "android-golib")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin.compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xannotation-default-target=param-property"
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

sentry {
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(true)
    uploadNativeSymbols.set(true)
    includeNativeSources.set(true)
    includeSourceContext.set(true)
    autoUploadSourceContext.set(true)
    tracingInstrumentation {
        enabled.set(true)
        logcat {
            enabled.set(true)
        }
    }
}

dependencies {
    // AndroidX & Core UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.android.svg)
    implementation(libs.kotlinx.collections.immutable)

    // Jetpack Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.material)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Dependency Injection (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Local Database (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Network & Serialization
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)

    // VPN Protocols
    implementation(libs.amneziawg.android)
    implementation(libs.go.vpn.lib)

    // Debug Tools
    debugImplementation(libs.leakcanary.android)

    // Sentry
    implementation(libs.sentry.android)
    implementation(libs.sentry.compose)
    implementation(libs.sentry.okhttp)
    implementation(libs.sentry.replay)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
