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

import android.annotation.SuppressLint
import java.util.Base64
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Trusts what Android trusts, plus Proton's alternative-routing endpoints.
 *
 * Proton's alternative routing reaches the API through hosts whose certificates are not issued by
 * a public CA; the official client accepts them only when the leaf certificate's key is one of
 * Proton's published pins. This class used to accept *every* chain the system rejected and rely on
 * pinning "afterwards" — but most hosts are not pinned at all, so any certificate, including an
 * attacker's self-signed one, was accepted for them. Now an untrusted chain is refused unless its
 * leaf key is pinned. The leaf is the only certificate whose private key the TLS handshake proves.
 */
@SuppressLint("CustomX509TrustManager")
class MirrorTrustManager(private val trustManagerOverride: X509ExtendedTrustManager? = null) : X509ExtendedTrustManager() {

    private val systemTrustManager: X509ExtendedTrustManager by lazy {
        trustManagerOverride?.let { return@lazy it }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        systemTrustManager.checkClientTrusted(chain, authType, socket)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        systemTrustManager.checkClientTrusted(chain, authType, engine)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        acceptPinnedLeafOr(chain) { systemTrustManager.checkServerTrusted(chain, authType) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        acceptPinnedLeafOr(chain) { systemTrustManager.checkServerTrusted(chain, authType, socket) }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        acceptPinnedLeafOr(chain) { systemTrustManager.checkServerTrusted(chain, authType, engine) }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return systemTrustManager.acceptedIssuers
    }

    private inline fun acceptPinnedLeafOr(chain: Array<out X509Certificate>?, systemCheck: () -> Unit) {
        try {
            systemCheck()
        } catch (error: CertificateException) {
            val leaf = chain?.firstOrNull() ?: throw error
            leaf.checkValidity()
            if (!isPinnedProtonLeaf(leaf)) throw error
        }
    }

    companion object {
        private val PROTON_LEAF_PINS: Set<String> =
            (NetworkConstants.DEFAULT_SPKI_PINS + NetworkConstants.ALTERNATIVE_API_SPKI_PINS).toSet()

        fun spkiPin(certificate: X509Certificate): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
            return Base64.getEncoder().encodeToString(hash)
        }

        /** True when the certificate's own key is one Proton publishes for its API. */
        fun isPinnedProtonLeaf(certificate: X509Certificate): Boolean = spkiPin(certificate) in PROTON_LEAF_PINS
    }
}
