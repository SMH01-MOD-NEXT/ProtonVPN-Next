package com.protonvpn.android.statistics

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Entity(tableName = "vpn_sessions")
data class VpnSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val serverName: String = "",
    val serverCountry: String = "",
    val protocol: String = "",
    val isConnectionError: Boolean = false,
    val errorMessage: String? = null,
    val downloadBytes: Long = 0,
    val uploadBytes: Long = 0
)

@Dao
interface StatisticsDao {
    @Insert
    suspend fun insertSession(session: VpnSessionEntity)

    @Query("SELECT * FROM vpn_sessions WHERE startTime >= :startRange AND startTime <= :endRange ORDER BY startTime DESC")
    fun getSessionsInTimeRange(startRange: Long, endRange: Long): Flow<List<VpnSessionEntity>>

    @Query("SELECT * FROM vpn_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<VpnSessionEntity>>
}

// Migration from version 1 to version 2: Add new columns for server tracking
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new columns to vpn_sessions table
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN serverName TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN serverCountry TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN protocol TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN isConnectionError INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN errorMessage TEXT")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN downloadBytes INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE vpn_sessions ADD COLUMN uploadBytes INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [VpnSessionEntity::class], version = 2)
abstract class StatisticsDatabase : RoomDatabase() {
    abstract fun statisticsDao(): StatisticsDao
}

@Module
@InstallIn(SingletonComponent::class)
object StatisticsModule {

    @Provides
    @Singleton
    fun provideStatisticsDatabase(@ApplicationContext context: Context): StatisticsDatabase {
        return Room.databaseBuilder(
            context,
            StatisticsDatabase::class.java,
            "vpn_stats_db"
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideStatisticsDao(db: StatisticsDatabase): StatisticsDao = db.statisticsDao()
}