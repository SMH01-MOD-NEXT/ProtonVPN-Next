// Application-level build script for Proton VPN-Next
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.room")
}

android {
    namespace = "ru.protonmod.next"
    compileSdk = 35

    // Force AGP to use a specific NDK version instead of the default one
    ndkVersion = "29.0.13113169" // Replace with your exact installed 29.x version if different

    defaultConfig {
        applicationId = "ru.protonmod.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // NDK configuration for VPN tunnels
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64", "armeabi-v7a"))
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            // Add custom dev endpoints or config fields here
            buildConfigField("boolean", "ALLOW_LOGCAT", "true")
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "ALLOW_LOGCAT", "false")
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
    }

    room {
        schemaDirectory("$projectDir/schemas")
        generateKotlin = true
    }
}

dependencies {
    // 1. AndroidX & Core UI
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 2. Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material:material-icons-extended")

    // Debug tools for Compose
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // 3. Dependency Injection (Hilt)
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // 4. Local Database (Room)
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // 5. Network & Serialization
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 6. VPN Protocols (Local modules)
    implementation(project(":amneziawg-android:tunnel"))

    // Proton Go VPN wrapper
    implementation("me.proton.vpn:go-vpn-lib:0.1.78")
}