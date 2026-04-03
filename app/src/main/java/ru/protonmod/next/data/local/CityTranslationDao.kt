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
import androidx.room.Transaction

@Dao
interface CityTranslationDao {
    @Query("SELECT localizedName FROM city_translations WHERE countryCode = :countryCode AND englishName = :englishName AND languageCode = :languageCode LIMIT 1")
    suspend fun getLocalizedName(countryCode: String, englishName: String, languageCode: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslations(translations: List<CityTranslationEntity>)

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

    /**
     * Atomically clears old city translations for a language and inserts new ones.
     * Uses @Transaction to batch the operations into a single database transaction,
     * avoiding N+1 query patterns when inserting multiple city translations.
     */
    @Transaction
    suspend fun upsertTranslations(languageCode: String, translations: List<CityTranslationEntity>) {
        clearTranslations(languageCode)
        insertTranslations(translations)
    }
}

