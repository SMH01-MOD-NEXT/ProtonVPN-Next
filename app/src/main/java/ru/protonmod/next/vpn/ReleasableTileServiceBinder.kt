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

package ru.protonmod.next.vpn

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import java.util.concurrent.atomic.AtomicReference

/**
 * Export this binder instead of TileService's inner IQSTileService.Stub, which holds its
 * service strongly. Release the delegate on destruction even if SystemUI retains our binder.
 * Uses only public Binder APIs and forwards the framework's protocol without duplicating AIDL.
 * This must not be an inner class or capture the service in a callback.
 */
internal class ReleasableTileServiceBinder(delegate: IBinder) : Binder() {
    private val delegate = AtomicReference<IBinder?>(delegate)

    init {
        attachInterface(null, delegate.interfaceDescriptor)
    }

    fun release() {
        delegate.set(null)
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        // Atomic publication is needed: Binder calls and lifecycle callbacks use different threads.
        // An already running call may finish; later calls cannot reach the destroyed service.
        val target = delegate.get() ?: return super.onTransact(code, data, reply, flags)
        return target.transact(code, data, reply, flags)
    }
}
