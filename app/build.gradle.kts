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

import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit

// Helper function to execute Git commands in the terminal
fun getGitOutput(command: String, workingDir: File): String {
    return try {
        val process = ProcessBuilder(command.split(" "))
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        process.waitFor(10, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        ""
    }
}

// Dynamically generate version name based on the latest Git tag
fun getDynamicVersionName(workingDir: File): String {
    val gitVersion = getGitOutput("git describe --tags --always", workingDir)
    // Fallback to "12.0.0" if Git is not available (e.g., downloaded as a ZIP)
    return gitVersion.ifEmpty { "12.0.0" }
}

// Dynamically generate version code using total commit count to ensure it strictly increases.
// Using total count instead of "since last tag" prevents resets when a new tag is created.
fun getDynamicVersionCode(workingDir: File): Int {
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
    alias(libs.plugins.sentry) apply false
}

// Only apply Sentry for non-privacy builds to ensure zero dependencies in privacy flavor
if (!project.gradle.startParameter.taskNames.any { it.contains("privacy", ignoreCase = true) }) {
    pluginManager.apply("io.sentry.android.gradle")
}

@Suppress("UnstableApiUsage")
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
        buildConfigField("boolean", "SENTRY_ENABLED", "true")

        // Support 64-bit architectures only
        // 32-bit devices (armeabi-v7a, armeabi) are not supported as the VPN engine
        // (AmneziaWG via go-vpn-lib) and its native libraries are compiled for 64-bit only
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Pre-calculate version info so it's consistent between Kotlin and C++
    val finalVersionCode = getDynamicVersionCode(rootDir)
    val finalVersionName = getDynamicVersionName(rootDir)

    defaultConfig {
        applicationId = "ru.protonmod.next"
        minSdk = 29
        targetSdk = 37
        versionCode = finalVersionCode
        versionName = finalVersionName

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags("-DEXPECTED_VERSION_CODE=$finalVersionCode")
                cppFlags("-DEXPECTED_VERSION_NAME=\\\"$finalVersionName\\\"")
                
                val signature = project.findProperty("EXPECTED_SIGNATURE") as? String 
                               ?: System.getenv("EXPECTED_SIGNATURE")
                if (signature != null) {
                    cppFlags("-DEXPECTED_SIGNATURE=\\\"$signature\\\"")
                }
            }
        }
    }

    flavorDimensions.addAll(listOf("channel", "type"))
    productFlavors {
        create("stable") {
            dimension = "channel"
            buildConfigField("String", "UPDATE_CHANNEL", "\"stable\"")
        }
        create("nightly") {
            dimension = "channel"
            isDefault = true
            applicationIdSuffix = ".nightly"
            versionNameSuffix = "-nightly"
            buildConfigField("String", "UPDATE_CHANNEL", "\"nightly\"")
        }
        
        create("standard") {
            dimension = "type"
            isDefault = true
            buildConfigField("boolean", "IS_PRIVACY_BUILD", "false")
        }

        create("privacy") {
            dimension = "type"
            applicationIdSuffix = ".privacy"
            buildConfigField("boolean", "IS_PRIVACY_BUILD", "true")
            buildConfigField("boolean", "SENTRY_ENABLED", "false")
            externalNativeBuild {
                cmake {
                    cppFlags("-DPRIVACY_FLAVOR=1")
                }
            }
        }
    }

    sourceSets {
        getByName("main") {
            java.directories.addAll(listOf("src/main/java", "src/main/kotlin"))
        }
        getByName("stable") {
            java.directories.add("src/stable/java")
        }
        getByName("nightly") {
            java.directories.add("src/nightly/java")
        }
        getByName("standard") {
            java.directories.add("src/standard/java")
        }
        getByName("privacy") {
            java.directories.add("src/privacy/java")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        val localProperties = Properties().apply {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { load(it) }
            }
        }

        val defaultDebugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")

        create("release") {
            val keyFile = System.getenv("SIGNING_KEY_FILE") ?: localProperties.getProperty("signing.release.keystore") ?: ""
            if (keyFile.isNotEmpty()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: localProperties.getProperty("signing.release.storePassword")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: localProperties.getProperty("signing.release.keyAlias")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: localProperties.getProperty("signing.release.keyPassword")
            } else {
                // Fallback to debug keystore if no release key provided
                val customDebugKeystore = localProperties.getProperty("signing.debug.keystore")
                if (customDebugKeystore != null) {
                    storeFile = file(customDebugKeystore)
                    storePassword = localProperties.getProperty("signing.debug.storePassword")
                    keyAlias = localProperties.getProperty("signing.debug.keyAlias")
                    keyPassword = localProperties.getProperty("signing.debug.keyPassword")
                } else {
                    storeFile = defaultDebugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }

        getByName("debug") {
            val customKeystore = localProperties.getProperty("signing.debug.keystore")
            if (customKeystore != null) {
                storeFile = file(customKeystore)
                storePassword = localProperties.getProperty("signing.debug.storePassword")
                keyAlias = localProperties.getProperty("signing.debug.keyAlias")
                keyPassword = localProperties.getProperty("signing.debug.keyPassword")
            } else {
                storeFile = defaultDebugKeystore
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
            
            externalNativeBuild {
                cmake {
                    cppFlags("-DDEBUG_BUILD=1")
                }
            }

            signingConfig = signingConfigs.getByName("release")

            packaging {
                jniLibs {
                    keepDebugSymbols.addAll(listOf(
                        "**/libam-go.so",
                        "**/libam-quick.so",
                        "**/libam.so",
                        "**/libandroidx.graphics.path.so",
                        "**/libdatastore_shared_counter.so",
                        "**/libgojni.so",
                        "**/libhev-socks5-tunnel.so"
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
        prefab = true
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

tasks.register("generateSecurityMetadata") {
    description = "Generates C++ security metadata header containing version info and official library lists"
    val outputDir = file("src/main/cpp")
    val outputFile = file("${outputDir}/security_metadata.h")
    
    val releaseMaxSize = 70 * 1024 * 1024L
    val debugMaxSize = 150 * 1024 * 1024L
    val expectedVersionCode = getDynamicVersionCode(rootDir)
    
    inputs.property("releaseMaxSize", releaseMaxSize)
    inputs.property("debugMaxSize", debugMaxSize)
    inputs.property("expectedVersionCode", expectedVersionCode)
    
    // List of known official libraries (including the ones we build)
    val officialLibs = listOf(
        "libam-go.so", "libam-quick.so", "libam.so", 
        "libandroidx.graphics.path.so", "libdatastore_shared_counter.so",
        "libhev-socks5-tunnel.so", "libbyedpi.so", "libnext.so"
    )
    val sentryLibs = listOf("libsentry-android.so", "libsentry.so")
    
    inputs.property("officialLibs", officialLibs)
    inputs.property("sentryLibs", sentryLibs)

    outputs.file(outputFile)

    doLast {
        if (!outputDir.exists()) outputDir.mkdirs()
        
        val content = """
            #ifndef NEXT_SECURITY_METADATA_H
            #define NEXT_SECURITY_METADATA_H

            #include <vector>
            #include <string>
            #include "obfuscation.h"

            // Obfuscated APK sizes
            #define MAX_RELEASE_APK_SIZE_VAL (${releaseMaxSize}LL ^ 0x1337BEEF)
            #define MAX_DEBUG_APK_SIZE_VAL   (${debugMaxSize}LL ^ 0xDEADBEEF)
            
            #ifdef PRIVACY_FLAVOR
            #define EXPECTED_LIB_COUNT ${officialLibs.size}
            #else
            #define EXPECTED_LIB_COUNT ${officialLibs.size + sentryLibs.size}
            #endif
            
            // Obfuscated version code
            #define EXPECTED_VERSION_CODE_VAL (${expectedVersionCode} ^ 0xCAFEBABE)

            namespace next {
                // We will use a function to get official libs to allow runtime decryption
                static inline std::vector<std::string> getOfficialLibs() {
                    return {
                        ${officialLibs.joinToString(",\n                        ") { "XOR_STR(\"$it\")" }},
#ifndef PRIVACY_FLAVOR
                        ${sentryLibs.joinToString(",\n                        ") { "XOR_STR(\"$it\")" }}
#endif
                    };
                }
                
                static inline long long getReleaseApkSize() { return MAX_RELEASE_APK_SIZE_VAL ^ 0x1337BEEF; }
                static inline long long getDebugApkSize() { return MAX_DEBUG_APK_SIZE_VAL ^ 0xDEADBEEF; }
                static inline int getVersionCode() { return EXPECTED_VERSION_CODE_VAL ^ 0xCAFEBABE; }
            }

            #endif // NEXT_SECURITY_METADATA_H
        """.trimIndent()
        
        outputFile.writeText(content)
    }
}

// Ensure security metadata is generated before CMake or any native tasks
tasks.configureEach {
    if (name.contains("externalNativeBuild") || name.contains("generateJsonModel")) {
        dependsOn("generateSecurityMetadata")
    }
}

// Also hook into preBuild to ensure it exists for IDE indexing
tasks.named("preBuild") {
    dependsOn("generateSecurityMetadata")
}

room {
    schemaDirectory("$projectDir/schemas")
    generateKotlin = true
}

plugins.withId("io.sentry.android.gradle") {
    configure<io.sentry.android.gradle.extensions.SentryPluginExtension> {
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
}

dependencies {
    // AndroidX & Core UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.multiprocess)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.android.svg)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.graphics.path)

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

    // Debug Tools
    val debugToolsImplementation = listOf(
        libs.androidx.compose.ui.tooling,
        libs.androidx.compose.ui.test.manifest,
        libs.leakcanary.android
    )

    debugToolsImplementation.forEach { tool ->
        add("debugImplementation", tool)
    }

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

    // Crypto
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pgp)
    implementation(libs.bcrypt)

    // Sentry - only for official builds (not for privacy)
    val sentryDeps = listOf(
        libs.sentry.android,
        libs.sentry.compose,
        libs.sentry.okhttp,
        libs.sentry.replay
    )
    add("standardImplementation", platform(libs.sentry.bom))
    sentryDeps.forEach { dep ->
        add("standardImplementation", dep)
    }

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
