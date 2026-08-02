/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.vpn

import kotlin.random.Random
import ru.protonmod.next.data.network.LogicalServer

internal object IpRotationSelector {
    private const val CANDIDATE_POOL_SIZE = 5

    fun select(
        servers: List<LogicalServer>,
        current: LogicalServer,
        maxTier: Int,
        keepCountry: Boolean,
        random: Random = Random.Default,
    ): LogicalServer? {
        val candidates = servers.asSequence()
            .filter { it.id != current.id }
            .filter { it.tier <= maxTier }
            .filter { !keepCountry || it.exitCountry == current.exitCountry }
            .filter { logical -> logical.servers.any { it.status == 1 && !it.wgPublicKey.isNullOrBlank() } }
            .sortedBy { it.averageLoad }
            .take(CANDIDATE_POOL_SIZE)
            .toList()
        return candidates.randomOrNull(random)
    }
}
