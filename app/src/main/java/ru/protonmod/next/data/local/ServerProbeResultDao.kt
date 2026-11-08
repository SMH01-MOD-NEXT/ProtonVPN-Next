/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Latest server scanner verdict for one physical server. */
@Entity(tableName = "server_probe_results")
data class ServerProbeResultEntity(
    @PrimaryKey val physicalServerId: String,
    val logicalServerId: String,
    val status: String,
    val detail: String,
    val endpoint: String?,
    val ports: String,
    val latencyMs: Int?,
    val checkedAt: Long,
)

@Dao
interface ServerProbeResultDao {
    @Query("SELECT * FROM server_probe_results")
    fun observeAll(): Flow<List<ServerProbeResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(results: List<ServerProbeResultEntity>)

    @Query("DELETE FROM server_probe_results")
    suspend fun clearAll()
}
