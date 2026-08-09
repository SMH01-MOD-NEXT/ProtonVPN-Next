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

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DatabaseModuleTest {
    @Test
    fun `busy timeout is configured through a query that consumes its result`() {
        val database = mock<SupportSQLiteDatabase>()
        val cursor = mock<Cursor>()
        whenever(database.query("PRAGMA busy_timeout = 5000")).thenReturn(cursor)

        DatabaseModule.configureBusyTimeout(database)

        verify(database).query("PRAGMA busy_timeout = 5000")
        verify(cursor).moveToFirst()
        verify(cursor).close()
    }
}
