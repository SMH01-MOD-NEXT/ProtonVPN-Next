/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.core.featureflags

import javax.inject.Inject
import javax.inject.Singleton
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Singleton
class VanillaFeatureFlagManager @Inject constructor() : FeatureFlagManager {

    override suspend fun fetchAndActivate() {
        // No-op for FOSS
    }

    override fun isStatisticsEnabled(): Boolean {
        return false
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VanillaFeatureFlagModule {
    @Binds
    abstract fun bindFeatureFlagManager(impl: VanillaFeatureFlagManager): FeatureFlagManager
}