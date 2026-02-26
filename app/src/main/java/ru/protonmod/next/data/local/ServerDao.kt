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

package ru.protonmod.next.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.data.network.PhysicalServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// --- Entity ---

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val city: String,
    val exitCountry: String,
    val tier: Int,
    val features: Int,
    val averageLoad: Int = 0,
    val physicalServersJson: String // Save physical servers as a JSON string
)

// --- DAO ---

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers")
    suspend fun getAllServers(): List<ServerEntity>

    @Query("SELECT * FROM servers")
    fun getServersFlow(): Flow<List<ServerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Query("DELETE FROM servers")
    suspend fun clearAllServers()
}

object ServerMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(server: LogicalServer): ServerEntity {
        return ServerEntity(
            id = server.id,
            name = server.name,
            city = server.city,
            exitCountry = server.exitCountry,
            tier = server.tier,
            features = server.features,
            averageLoad = server.averageLoad,
            physicalServersJson = json.encodeToString(server.servers)
        )
    }

    fun toDomain(entity: ServerEntity): LogicalServer {
        return LogicalServer(
            id = entity.id,
            name = entity.name,
            city = entity.city,
            exitCountry = entity.exitCountry,
            entryCountry = entity.exitCountry,
            tier = entity.tier,
            features = entity.features,
            servers = try {
                json.decodeFromString<List<PhysicalServer>>(entity.physicalServersJson)
            } catch (e: Exception) {
                emptyList()
            },
            averageLoad = entity.averageLoad
        )
    }
}
