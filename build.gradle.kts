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
