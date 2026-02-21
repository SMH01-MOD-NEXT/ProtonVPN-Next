// Root configuration for Proton VPN-Next project
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://clojars.org/repo/") }
    }
}

rootProject.name = "ProtonVpnNext"

// Include main application module
include(":app")

// Include modern VPN tunnels (AmneziaWG for DPI evasion)
include(":amneziawg-android:tunnel")