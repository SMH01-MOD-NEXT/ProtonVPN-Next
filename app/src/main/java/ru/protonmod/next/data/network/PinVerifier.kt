/*
 * Copyright (C) 2026 SMH01
 * TLS hardening adapted from drxekus, commit 0c7543e (GPL-3.0).
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

package ru.protonmod.next.data.network

import java.security.cert.X509Certificate
import javax.net.ssl.SSLSession

/**
 * Accepts a TLS session whose certificate does not name the requested host (an IP literal or a
 * Proton alternative-routing domain) only when the server's own leaf key is pinned.
 *
 * Any certificate of the chain used to be enough. The handshake proves possession of the leaf's
 * key only, so a server could present its own leaf next to a genuine pinned intermediate or root
 * and pass.
 */
object PinVerifier {
    fun check(session: SSLSession, allowedPins: List<String>): Boolean {
        return try {
            val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
            MirrorTrustManager.spkiPin(leaf) in allowedPins
        } catch (e: Exception) {
            false
        }
    }
}
