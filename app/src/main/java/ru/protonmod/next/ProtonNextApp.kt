package ru.protonmod.next

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for Proton VPN-Next.
 * The @HiltAndroidApp annotation triggers Hilt's code generation,
 * including a base class for your application that serves as the
 * application-level dependency container.
 */
@HiltAndroidApp
class ProtonNextApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Here you can initialize other global libraries if needed in the future
    }
}