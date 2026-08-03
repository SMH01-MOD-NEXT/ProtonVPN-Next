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

package ru.protonmod.next.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.protonmod.next.data.local.AppDatabase
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.ServersCacheDao
import ru.protonmod.next.data.local.ServerDao
import ru.protonmod.next.data.local.RecentConnectionDao
import ru.protonmod.next.data.local.ProfileDao
import ru.protonmod.next.data.local.CityTranslationDao
import ru.protonmod.next.data.local.TrafficStatsDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {}
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session ADD COLUMN userTier INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(servers)")
            var columnExists = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "averageLoad") {
                    columnExists = true
                    break
                }
            }
            cursor.close()
            
            if (!columnExists) {
                db.execSQL("ALTER TABLE servers ADD COLUMN averageLoad INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers_cache ADD COLUMN lastModified TEXT")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`protocol` TEXT NOT NULL, " +
                        "`port` INTEGER NOT NULL, " +
                        "`isObfuscationEnabled` INTEGER NOT NULL, " +
                        "`autoOpenUrl` TEXT, " +
                        "`targetServerId` TEXT, " +
                        "`targetCountry` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Check if column exists before adding to prevent crashes on partial migrations
            val cursor = db.query("PRAGMA table_info(profiles)")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            cursor.close()

            if (!columns.contains("targetCity")) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN targetCity TEXT")
            }
            if (!columns.contains("obfuscationProfileId")) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN obfuscationProfileId TEXT")
            }
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session ADD COLUMN wgCertificate TEXT")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `city_translations` (" +
                        "`countryCode` TEXT NOT NULL, " +
                        "`englishName` TEXT NOT NULL, " +
                        "`localizedName` TEXT NOT NULL, " +
                        "`languageCode` TEXT NOT NULL, " +
                        "PRIMARY KEY(`countryCode`, `englishName`, `languageCode`))"
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `city_cache` (" +
                        "`languageCode` TEXT NOT NULL, " +
                        "`lastUpdated` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`languageCode`))"
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session ADD COLUMN vpnIpv4 TEXT")
            db.execSQL("ALTER TABLE session ADD COLUMN vpnIpv6 TEXT")
            db.execSQL("ALTER TABLE session ADD COLUMN vpnDns TEXT")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val sessionCursor = db.query("PRAGMA table_info(session)")
            val sessionColumns = mutableListOf<String>()
            while (sessionCursor.moveToNext()) {
                sessionColumns.add(sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("name")))
            }
            sessionCursor.close()

            if (!sessionColumns.contains("certExpiresAt")) {
                db.execSQL("ALTER TABLE session ADD COLUMN certExpiresAt INTEGER NOT NULL DEFAULT 0")
            }
            if (!sessionColumns.contains("certRefreshAt")) {
                db.execSQL("ALTER TABLE session ADD COLUMN certRefreshAt INTEGER NOT NULL DEFAULT 0")
            }

            val cacheCursor = db.query("PRAGMA table_info(servers_cache)")
            val cacheColumns = mutableListOf<String>()
            while (cacheCursor.moveToNext()) {
                cacheColumns.add(cacheCursor.getString(cacheCursor.getColumnIndexOrThrow("name")))
            }
            cacheCursor.close()

            if (!cacheColumns.contains("statusId")) {
                db.execSQL("ALTER TABLE servers_cache ADD COLUMN statusId TEXT")
            }
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // No schema changes, but incrementing version forces a migration check 
            // which can be used as a trigger for app updates.
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {}
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Force logout and clear sensitive data on update due to major auth refactor.
            // General settings (DataStore), profiles, and connection history are preserved.
            db.execSQL("DELETE FROM session")
            db.execSQL("DELETE FROM servers")
            db.execSQL("DELETE FROM servers_cache")
            db.execSQL("DELETE FROM city_translations")
            db.execSQL("DELETE FROM city_cache")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val sessionCursor = db.query("PRAGMA table_info(session)")
            val sessionColumns = mutableListOf<String>()
            while (sessionCursor.moveToNext()) {
                sessionColumns.add(sessionCursor.getString(sessionCursor.getColumnIndexOrThrow("name")))
            }
            sessionCursor.close()

            if (!sessionColumns.contains("wgAlternateCertificate")) {
                db.execSQL("ALTER TABLE session ADD COLUMN wgAlternateCertificate TEXT")
            }
            if (!sessionColumns.contains("altCertExpiresAt")) {
                db.execSQL("ALTER TABLE session ADD COLUMN altCertExpiresAt INTEGER NOT NULL DEFAULT 0")
            }
            if (!sessionColumns.contains("altCertRefreshAt")) {
                db.execSQL("ALTER TABLE session ADD COLUMN altCertRefreshAt INTEGER NOT NULL DEFAULT 0")
            }
            if (!sessionColumns.contains("isExtendedCertEnabled")) {
                db.execSQL("ALTER TABLE session ADD COLUMN isExtendedCertEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Daily VPN traffic statistics for the redesigned dashboard.
            // NOTE: no DEFAULT clauses here on purpose - Room validates the
            // migrated schema against the entity definition exactly.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `traffic_stats` (" +
                        "`day` TEXT NOT NULL, " +
                        "`rxBytes` INTEGER NOT NULL, " +
                        "`txBytes` INTEGER NOT NULL, " +
                        "`usageSeconds` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`day`))"
            )
        }
    }

    /**
     * Room writes are owned by the main process; WAL keeps readers unblocked and this timeout is a
     * final safeguard for OS/database maintenance contention. The dedicated `:vpn` process must not
     * open or write this database because a lock failure there would terminate the tunnel process.
     */
    private const val BUSY_TIMEOUT_MS = 5_000

    private val BUSY_TIMEOUT_CALLBACK = object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            // busy_timeout is a per-connection setting and WAL mode makes the framework open a
            // pool of connections, while onOpen only runs for the first one. Without registering
            // the pragma for every pooled connection the extra readers/writers keep failing
            // immediately with SQLITE_BUSY instead of waiting (ANDROID-228).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                db.execPerConnectionSQL("PRAGMA busy_timeout = $BUSY_TIMEOUT_MS", null)
            } else {
                db.query("PRAGMA busy_timeout = $BUSY_TIMEOUT_MS").use { it.moveToFirst() }
            }
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "proton_next_db"
        )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(BUSY_TIMEOUT_CALLBACK)
        .addMigrations(MIGRATION_4_5)
        .addMigrations(MIGRATION_5_6)
        .addMigrations(MIGRATION_6_7)
        .addMigrations(MIGRATION_7_8)
        .addMigrations(MIGRATION_8_9)
        .addMigrations(MIGRATION_9_10)
        .addMigrations(MIGRATION_10_11)
        .addMigrations(MIGRATION_11_12)
        .addMigrations(MIGRATION_12_13)
        .addMigrations(MIGRATION_13_14)
        .addMigrations(MIGRATION_14_15)
        .addMigrations(MIGRATION_15_16)
        .addMigrations(MIGRATION_16_17)
        .addMigrations(MIGRATION_17_18)
        .addMigrations(MIGRATION_18_19)
        .addMigrations(MIGRATION_19_20)
        .build()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    @Singleton
    fun provideServersCacheDao(database: AppDatabase): ServersCacheDao {
        return database.serversCacheDao()
    }

    @Provides
    @Singleton
    fun provideServerDao(database: AppDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    @Singleton
    fun provideRecentConnectionDao(database: AppDatabase): RecentConnectionDao {
        return database.recentConnectionDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideCityTranslationDao(database: AppDatabase): CityTranslationDao {
        return database.cityTranslationDao()
    }

    @Provides
    @Singleton
    fun provideTrafficStatsDao(database: AppDatabase): TrafficStatsDao {
        return database.trafficStatsDao()
    }
}
