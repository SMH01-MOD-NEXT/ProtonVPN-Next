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

package ru.protonmod.next.data.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.protonmod.next.data.network.LogicalServer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared state for tracking the currently connected VPN server.
 * This allows different screens (Dashboard, Countries) to access and update
 * the connected server information.
 */
@Singleton
class ConnectedServerState @Inject constructor() {

    private val _connectedServer = MutableStateFlow<LogicalServer?>(null)
    val connectedServer: StateFlow<LogicalServer?> = _connectedServer.asStateFlow()

    fun setConnectedServer(server: LogicalServer?) {
        _connectedServer.value = server
    }
}
