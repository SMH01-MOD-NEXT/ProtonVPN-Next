package ru.protonmod.next.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

// --- Entities ---

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: Int = 1, // We only store one active user session
    val accessToken: String,
    val refreshToken: String,
    val sessionId: String,
    val userId: String,
    val wgPrivateKey: String? = null,
    val wgPublicKeyPem: String? = null
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
}

// --- Database ---

@Database(entities = [SessionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}