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

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

// --- Entities ---

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int = 1, // We only store one active user session
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
    val userId: String,
    val userTier: Int = 0, // 0: Free, 1: Basic, 2: Plus
    val wgPrivateKey: String? = null,
    val wgPublicKeyPem: String? = null
)

@Entity(tableName = "servers_cache")
data class ServersCacheEntity(
    @PrimaryKey val id: Int = 1, // We only store one cache entry
    val cachedAt: Long, // Timestamp in milliseconds
    val expiresAt: Long // Timestamp when cache expires
)


// --- DAO ---

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE id = 1")
    suspend fun getSession(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: SessionEntity)

    @Query("DELETE FROM session")
    suspend fun clearSession()
    
    @Query("UPDATE session SET userTier = :tier WHERE id = 1")
    suspend fun updateUserTier(tier: Int)
}

@Dao
interface ServersCacheDao {
    @Query("SELECT * FROM servers_cache WHERE id = 1")
    suspend fun getCacheInfo(): ServersCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCacheInfo(cache: ServersCacheEntity)

    @Query("DELETE FROM servers_cache")
    suspend fun clearCacheInfo()
}
