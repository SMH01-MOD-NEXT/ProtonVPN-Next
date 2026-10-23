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

import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/** Real Android Binder/Parcel tests, without connecting VPN or altering the installed nightly app. */
@RunWith(AndroidJUnit4::class)
class TileServiceBinderInstrumentedTest {
    @Test
    fun forwardsTransactionDataReplyAndFlags() {
        val target = RecordingBinder()
        val binder = ReleasableTileServiceBinder(target)
        assertEquals(DESCRIPTOR, binder.interfaceDescriptor)
        assertNull(binder.queryLocalInterface(DESCRIPTOR))
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(42)
            assertTrue(binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0))
            reply.readException()
            assertEquals(43, reply.readInt())
            assertEquals(42, target.value)
            assertEquals(0, target.flags)
        } finally {
            data.recycle()
            reply.recycle()
            binder.release()
        }
    }

    @Test
    fun forwardsOneWayCallsAndDropsLateCallsAfterIdempotentRelease() {
        val target = RecordingBinder()
        val binder = ReleasableTileServiceBinder(target)
        assertTrue(send(binder, 7))
        assertEquals(IBinder.FLAG_ONEWAY, target.flags)
        binder.release()
        binder.release()
        assertFalse(send(binder, 8))
        assertEquals(7, target.value)
        assertEquals(DESCRIPTOR, binder.interfaceDescriptor)
    }

    @Test
    fun frameworkBinderProtocolSurvivesWrappingAndEveryBindingIsReleasedOnDestroy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var retained: RetainedBindings
        instrumentation.runOnMainSync {
            val service = VpnTileService()
            // Minimal SystemUI getTile response: no tile means no observation/connect side effects.
            val systemUi = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
                    data.enforceInterface("android.service.quicksettings.IQSService")
                    data.readStrongBinder()
                    requireNotNull(reply).writeNoException()
                    reply.writeInt(0) // null Tile
                    return true
                }
            }
            val extras = Bundle().apply {
                putBinder("service", systemUi)
                putBinder("token", Binder())
            }
            val intent = Intent().putExtras(extras)
            val first = requireNotNull(service.onBind(intent))
            val second = requireNotNull(service.onBind(intent))
            assertEquals("android.service.quicksettings.IQSTileService", first.interfaceDescriptor)
            assertTrue(frameworkTileAdded(first))
            assertTrue(frameworkTileAdded(second))
            retained = RetainedBindings(listOf(first, second), WeakReference(service))
        }
        instrumentation.waitForIdleSync() // drain the actual TileService handler callbacks
        instrumentation.runOnMainSync {
            requireNotNull(retained.service.get()).onDestroy()
        }
        instrumentation.waitForIdleSync()
        retained.binders.forEach { assertFalse(frameworkTileAdded(it)) }
        // Keep both exported binders alive, just as a stale SystemUI/native reference would.
        repeat(30) {
            if (retained.service.get() == null) return@repeat
            Runtime.getRuntime().gc()
            System.runFinalization()
            SystemClock.sleep(100)
        }
        assertNull("Exported binders retained destroyed VpnTileService", retained.service.get())
        assertEquals(2, retained.binders.size)
    }

    private fun frameworkTileAdded(binder: IBinder): Boolean {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("android.service.quicksettings.IQSTileService")
            return binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    private fun send(binder: IBinder, value: Int): Boolean {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(value)
            return binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    private data class RetainedBindings(val binders: List<IBinder>, val service: WeakReference<VpnTileService>)

    private class RecordingBinder : Binder() {
        var value = 0
        var flags = 0
        init { attachInterface(null, DESCRIPTOR) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != IBinder.FIRST_CALL_TRANSACTION) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            value = data.readInt()
            this.flags = flags
            reply?.writeNoException()
            reply?.writeInt(value + 1)
            return true
        }
    }

    companion object {
        private const val DESCRIPTOR = "ru.protonmod.next.test.TileBinder"
    }
}
