/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.protonmod.next.data.local.ServerProbeResultDao
import ru.protonmod.next.data.local.ServerProbeResultEntity
import ru.protonmod.next.data.network.probe.ServerAvailability
import ru.protonmod.next.data.network.probe.ServerProbeDetail
import ru.protonmod.next.data.network.probe.ServerProbeResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Latest scanner verdict per physical server. Verdicts describe the user's network rather than
 * the account, so they are kept across logins until the user clears them.
 */
@Singleton
class ServerProbeRepository @Inject constructor(
    private val dao: ServerProbeResultDao,
) {
    /** Results keyed by physical server id. */
    val results: Flow<Map<String, ServerProbeResult>> = dao.observeAll().map { entities ->
        entities.mapNotNull { it.toResult() }.associateBy { it.physicalServerId }
    }

    suspend fun save(results: List<ServerProbeResult>) {
        if (results.isNotEmpty()) dao.upsertAll(results.map { it.toEntity() })
    }

    suspend fun clear() {
        dao.clearAll()
    }

    private fun ServerProbeResultEntity.toResult(): ServerProbeResult? {
        val availability = ServerAvailability.entries.firstOrNull { it.name == status } ?: return null
        val probeDetail = ServerProbeDetail.entries.firstOrNull { it.name == detail } ?: return null
        return ServerProbeResult(
            physicalServerId = physicalServerId,
            logicalServerId = logicalServerId,
            availability = availability,
            detail = probeDetail,
            endpoint = endpoint,
            ports = ports.split(',').mapNotNull { it.trim().toIntOrNull() },
            latencyMs = latencyMs,
            checkedAt = checkedAt,
        )
    }

    private fun ServerProbeResult.toEntity() = ServerProbeResultEntity(
        physicalServerId = physicalServerId,
        logicalServerId = logicalServerId,
        status = availability.name,
        detail = detail.name,
        endpoint = endpoint,
        ports = ports.joinToString(","),
        latencyMs = latencyMs,
        checkedAt = checkedAt,
    )
}
