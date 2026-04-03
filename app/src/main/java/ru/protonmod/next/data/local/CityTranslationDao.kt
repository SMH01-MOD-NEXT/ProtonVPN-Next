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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery

@Dao
interface CityTranslationDao {
    @Query("SELECT localizedName FROM city_translations WHERE countryCode = :countryCode AND englishName = :englishName AND languageCode = :languageCode LIMIT 1")
    suspend fun getLocalizedName(countryCode: String, englishName: String, languageCode: String): String?

    /**
     * Batch insert city translations using a single optimized SQL statement.
     * This replaces the individual INSERT OR REPLACE calls with a single
     * multi-row INSERT statement to avoid N+1 query performance issues.
     */
    suspend fun insertTranslations(translations: List<CityTranslationEntity>) {
        if (translations.isEmpty()) return
        
        // Build a single INSERT OR REPLACE statement with multiple rows
        val valuesClause = translations.joinToString(", ") { entity ->
            val countryCode = entity.countryCode.replace("'", "''")
            val englishName = entity.englishName.replace("'", "''")
            val localizedName = entity.localizedName.replace("'", "''")
            val languageCode = entity.languageCode.replace("'", "''")
            "('$countryCode', '$englishName', '$localizedName', '$languageCode')"
        }
        
        val sql = "INSERT OR REPLACE INTO city_translations (countryCode, englishName, localizedName, languageCode) VALUES $valuesClause"
        insertTranslationsRaw(SimpleSQLiteQuery(sql))
    }

    @RawQuery
    suspend fun insertTranslationsRaw(query: SupportSQLiteQuery)

    @Query("DELETE FROM city_translations WHERE languageCode = :languageCode")
    suspend fun clearTranslations(languageCode: String)

    @Query("DELETE FROM city_translations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM city_translations WHERE languageCode = :languageCode")
    suspend fun getCount(languageCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCacheInfo(cache: CityCacheEntity)

    @Query("SELECT lastUpdated FROM city_cache WHERE languageCode = :languageCode")
    suspend fun getLastUpdated(languageCode: String): Long?

    @Query("DELETE FROM city_cache")
    suspend fun clearCacheInfo()
}

