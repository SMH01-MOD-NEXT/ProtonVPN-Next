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

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SessionEntity
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.local.ConnectionVerificationMode
import ru.protonmod.next.netshield.LocalNetShield
import ru.protonmod.next.netshield.NetShieldLevel
import ru.protonmod.next.data.network.CreateCertificateResponse
import ru.protonmod.next.data.network.PhysicalServer
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.data.repository.VpnRepository
import ru.protonmod.next.data.state.ConnectedServerState
import ru.protonmod.next.utils.coroutines.DispatcherProvider
import ru.protonmod.next.utils.crypto.CryptoWrapper
import ru.protonmod.next.utils.crypto.VpnKeyPair
import ru.protonmod.next.utils.system.SystemContextWrapper
import java.net.Inet4Address
import java.net.InetAddress
import org.mockito.Mockito
import ru.protonmod.next.vpn.VpnTunnelState
import org.junit.Assert.assertEquals
import org.mockito.MockedStatic
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AmneziaVpnManagerTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var settingsManager: SettingsManager

    @Mock
    private lateinit var vpnRepository: VpnRepository

    @Mock
    private lateinit var sessionDao: SessionDao

    @Mock
    private lateinit var connectedServerState: ConnectedServerState

    @Mock
    private lateinit var systemContextWrapper: SystemContextWrapper
    
    @Mock
    private lateinit var cryptoWrapper: CryptoWrapper
    
    @Mock
    private lateinit var awgBoxConfigGenerator: AwgBoxConfigGenerator

    @Mock
    private lateinit var localNetShield: LocalNetShield

    @Mock
    private lateinit var authRepository: AuthRepository

    @Mock
    private lateinit var nextVpnManager: NextVpnManager

    @Mock
    private lateinit var vpnNetworkMonitor: VpnNetworkMonitor

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    
    private val testDispatcherProvider = object : DispatcherProvider {
        override fun main(): CoroutineDispatcher = testDispatcher
        override fun io(): CoroutineDispatcher = testDispatcher
        override fun default(): CoroutineDispatcher = testDispatcher
    }

    private lateinit var manager: AmneziaVpnManager
    private lateinit var mockedInetAddress: MockedStatic<InetAddress>
    private lateinit var mockedCertFactory: MockedStatic<CertificateFactory>

    @Before
    fun setup() {
        runBlocking {
            MockitoAnnotations.openMocks(this@AmneziaVpnManagerTest)
            
            mockedInetAddress = Mockito.mockStatic(InetAddress::class.java)
            mockedCertFactory = Mockito.mockStatic(CertificateFactory::class.java)
            
            val mockAddress = Mockito.mock(Inet4Address::class.java)
            whenever(mockAddress.hostAddress).thenReturn("1.2.3.4")
            mockedInetAddress.`when`<InetAddress> { InetAddress.getByName(any()) }.thenReturn(mockAddress)
            mockedInetAddress.`when`<Array<InetAddress>> { InetAddress.getAllByName(any()) }
                .thenReturn(arrayOf(mockAddress))
            
            val mockCf = Mockito.mock(CertificateFactory::class.java)
            val mockCert = Mockito.mock(X509Certificate::class.java)
            whenever(mockCert.notAfter).thenReturn(Date(System.currentTimeMillis() + 10000000))
            whenever(mockCf.generateCertificate(any())).thenReturn(mockCert)
            mockedCertFactory.`when`<CertificateFactory> { CertificateFactory.getInstance(any()) }.thenReturn(mockCf)

            whenever(context.applicationContext).thenReturn(context)
            whenever(context.packageName).thenReturn("ru.protonmod.next")
            whenever(context.applicationInfo).thenReturn(android.content.pm.ApplicationInfo().apply { nativeLibraryDir = "/data/app/lib/arm64" })
            
            whenever(settingsManager.notificationsEnabled).thenReturn(flowOf(true))
            whenever(settingsManager.killSwitchEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.splitTunnelingEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.vpnPort).thenReturn(flowOf(1194))
            whenever(settingsManager.obfuscationEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.proxyChainEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.proxyChainConfig).thenReturn(flowOf(""))
            whenever(settingsManager.customDns).thenReturn(flowOf(""))
            whenever(settingsManager.netShieldLevel).thenReturn(flowOf(NetShieldLevel.DISABLED))
            whenever(settingsManager.torModeEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.ipRotationEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.ipRotationIntervalMinutes).thenReturn(flowOf(SettingsManager.DEFAULT_IP_ROTATION_INTERVAL_MINUTES))
            whenever(settingsManager.ipRotationKeepCountry).thenReturn(flowOf(true))
            whenever(localNetShield.activeRuleSets(NetShieldLevel.DISABLED)).thenReturn(emptyList())
            whenever(settingsManager.pauseEndTime).thenReturn(flowOf(0L))
            whenever(settingsManager.allowLanEnabled).thenReturn(flowOf(false))
            whenever(settingsManager.splitTunnelingMode).thenReturn(flowOf("exclude"))
            whenever(settingsManager.excludedApps).thenReturn(flowOf(emptySet()))
            whenever(settingsManager.excludedIps).thenReturn(flowOf(emptySet()))
            whenever(settingsManager.excludedDomains).thenReturn(flowOf(emptySet()))
            whenever(settingsManager.sentryNonFatalEnabled).thenReturn(flowOf(true))
            whenever(settingsManager.analyticsEnabled).thenReturn(flowOf(true))
            whenever(settingsManager.connectionVerificationMode).thenReturn(flowOf(ConnectionVerificationMode.BALANCED))
            whenever(settingsManager.connectionVerificationRequired).thenReturn(flowOf(false))
            whenever(settingsManager.connectionPreflightRequired).thenReturn(flowOf(false))
            whenever(settingsManager.connectionFailureDetection).thenReturn(flowOf(true))
            whenever(settingsManager.connectionAutoReconnect).thenReturn(flowOf(true))
            
            whenever(settingsManager.awgJc).thenReturn(flowOf(3))
            whenever(settingsManager.awgJmin).thenReturn(flowOf(1))
            whenever(settingsManager.awgJmax).thenReturn(flowOf(3))
            whenever(settingsManager.awgS1).thenReturn(flowOf(0))
            whenever(settingsManager.awgS2).thenReturn(flowOf(0))
            whenever(settingsManager.awgS3).thenReturn(flowOf(0))
            whenever(settingsManager.awgS4).thenReturn(flowOf(0))
            whenever(settingsManager.awgH1).thenReturn(flowOf("1"))
            whenever(settingsManager.awgH2).thenReturn(flowOf("2"))
            whenever(settingsManager.awgH3).thenReturn(flowOf("3"))
            whenever(settingsManager.awgH4).thenReturn(flowOf("4"))
            whenever(settingsManager.awgI1).thenReturn(flowOf("i1"))
            whenever(settingsManager.awgI2).thenReturn(flowOf(""))
            whenever(settingsManager.awgI3).thenReturn(flowOf(""))
            whenever(settingsManager.awgI4).thenReturn(flowOf(""))
            whenever(settingsManager.awgI5).thenReturn(flowOf(""))

            whenever(connectedServerState.connectedServer).thenReturn(MutableStateFlow(null))
            whenever(vpnRepository.getCachedServers()).thenReturn(emptyList())
            
            whenever(cryptoWrapper.generateVpnKeyPair()).thenReturn(VpnKeyPair("pub", "priv"))
            whenever(awgBoxConfigGenerator.buildConfig(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any(), any(), any()))
                .thenReturn("mock_config")
            val verificationCycle = VpnNetworkMonitor.VerificationCycle(1, emptySet())
            whenever(vpnNetworkMonitor.beginVerificationCycle()).thenReturn(verificationCycle)
            whenever(vpnNetworkMonitor.awaitUsable(any(), any(), any())).thenReturn(false)
            whenever(vpnNetworkMonitor.prepareUnderlyingConnection(any(), anyOrNull())).thenReturn(null)
            whenever(vpnNetworkMonitor.resolveIpv4OnUnderlying(any())).thenReturn(null)

            manager = AmneziaVpnManager(
                context,
                settingsManager,
                { vpnRepository },
                sessionDao,
                connectedServerState,
                systemContextWrapper,
                cryptoWrapper,
                awgBoxConfigGenerator,
                localNetShield,
                nextVpnManager,
                { authRepository },
                vpnNetworkMonitor,
                testDispatcherProvider,
                testScope
            )
        }
    }

    @After
    fun tearDown() {
        mockedInetAddress.close()
        mockedCertFactory.close()
    }

    @Test
    fun `disconnect calls stopVpnService`() = runTest(testDispatcher) {
        manager.disconnect()
        verify(systemContextWrapper).stopVpnService()
    }

    @Test
    fun `forceRefreshCertificate updates both certificate and private key`() = runTest(testDispatcher) {
        val oldSession = SessionEntity(
            accessToken = "at",
            refreshToken = "rt",
            sessionId = "sid",
            userId = "uid",
            wgPrivateKey = "old_priv",
            wgPublicKeyPem = "old_pub_pem",
            wgCertificate = "old_cert"
        )
        whenever(sessionDao.getSession()).thenReturn(oldSession)
        
        val newKeys = VpnKeyPair("new_pub_pem", "new_priv")
        whenever(cryptoWrapper.generateVpnKeyPair()).thenReturn(newKeys)
        
        val refreshResponse = CreateCertificateResponse(code = 1000, certificate = "new_cert", expirationTime = 0, refreshTime = 0)
        whenever(vpnRepository.registerWireGuardKey(eq("at"), eq("sid"), anyOrNull()))
            .thenReturn(Result.success(Pair(refreshResponse, newKeys)))
        
        manager.forceRefreshCertificate()
        
        // Verify that updateVpnKeys was called with NEW private key and NEW certificate
        verify(sessionDao).updateVpnKeys(
            privateKey = eq("new_priv"),
            publicKeyPem = eq("new_pub_pem"),
            certificate = eq("new_cert"),
            expiresAt = eq(0L),
            refreshAt = eq(0L)
        )
    }

    @Test
    fun `connect calls startVpnService with correct config`() = runTest(testDispatcher) {
        val server = PhysicalServer(
            id = "server_1",
            domain = "node.protonvpn.com",
            status = 1,
            wgPublicKey = "pubkey"
        )
        val session = SessionEntity(
            accessToken = "at",
            refreshToken = "rt",
            sessionId = "sid",
            userId = "uid",
            wgPrivateKey = "privkey",
            wgPublicKeyPem = "pubkeypem",
            wgCertificate = "cert"
        )

        whenever(sessionDao.getSession()).thenReturn(session)
        val certResponse = CreateCertificateResponse(code = 1000, certificate = "new_cert", expirationTime = 0L, refreshTime = 0L)
        whenever(vpnRepository.registerWireGuardKey(any(), any(), anyOrNull())).thenReturn(
            Result.success(Pair(certResponse, VpnKeyPair("pubkeypem", "privkey")))
        )

        manager.connect("logical_1", server, session)
        
        advanceUntilIdle()

        verify(systemContextWrapper).startVpnService(
            configStr = eq("mock_config"),
            logicalServerId = eq("logical_1"),
            sessionId = any(),
            notificationsEnabled = any(),
            killSwitchEnabled = any(),
            verificationMode = eq(ConnectionVerificationMode.BALANCED),
            verificationRequired = eq(false),
            failureDetectionEnabled = eq(true),
            autoReconnectEnabled = eq(true),
            splitTunnelingEnabled = eq(false),
            splitTunnelingMode = eq("exclude"),
            excludedApps = any(),
            excludedIps = any()
        )
    }

    @Test
    fun `include mode keeps only the user app selection in generated config`() = runTest(testDispatcher) {
        val server = PhysicalServer(
            id = "server_1",
            domain = "node.protonvpn.com",
            status = 1,
            wgPublicKey = "pubkey"
        )
        val session = SessionEntity(
            accessToken = "at",
            refreshToken = "rt",
            sessionId = "sid",
            userId = "uid",
            wgPrivateKey = "privkey",
            wgPublicKeyPem = "pubkeypem",
            wgCertificate = "cert"
        )
        whenever(sessionDao.getSession()).thenReturn(session)
        whenever(settingsManager.splitTunnelingEnabled).thenReturn(flowOf(true))
        whenever(settingsManager.splitTunnelingMode).thenReturn(flowOf("include"))
        whenever(settingsManager.excludedApps).thenReturn(flowOf(setOf("org.telegram.messenger")))

        manager.connect("logical_1", server, session)
        advanceUntilIdle()

        verify(awgBoxConfigGenerator).buildConfig(
            any(), any(), any(), any(), any(),
            eq(true),
            any(),
            eq(setOf("org.telegram.messenger")),
            any(), any(), any(), any(), any(), isNull(), any(), any(), any(), any(), any()

        )
    }

    @Test
    fun `VPN state transitions to CONNECTED when the new VPN network is usable`() = runTest(testDispatcher) {
        whenever(vpnNetworkMonitor.awaitUsable(any(), any(), any())).thenReturn(true)

        manager.handleTunnelStateChange(VpnTunnelState.UP)
        advanceUntilIdle()

        assertEquals(AmneziaVpnManager.VpnState.CONNECTED, manager.vpnState.value)
        verify(vpnNetworkMonitor, times(1)).awaitUsable(any(), any(), any())
        verify(systemContextWrapper, times(1)).setVpnVerified()
    }

    @Test
    fun `cancelled verification is not converted into a connected state`() = runTest(testDispatcher) {
        whenever(settingsManager.connectionVerificationRequired).thenReturn(flowOf(true))
        whenever(vpnNetworkMonitor.awaitUsable(any(), any(), any())).thenAnswer {
            throw kotlinx.coroutines.CancellationException("test cancellation")
        }

        manager.handleTunnelStateChange(VpnTunnelState.UP)
        advanceUntilIdle()

        assertEquals(AmneziaVpnManager.VpnState.VERIFYING, manager.vpnState.value)
        verify(systemContextWrapper, never()).setVpnVerified()
    }

    @Test
    fun `connect passes allowLan setting to config generator`() = runTest(testDispatcher) {
        val server = PhysicalServer(id = "s1", domain = "d1", status = 1, wgPublicKey = "pk")
        val session = SessionEntity(
            accessToken = "at",
            refreshToken = "rt",
            sessionId = "sid",
            userId = "uid",
            wgPrivateKey = "priv",
            wgCertificate = "cert"
        )
        
        whenever(sessionDao.getSession()).thenReturn(session)
        whenever(settingsManager.allowLanEnabled).thenReturn(flowOf(true))
        
        manager.connect("l1", server, session)
        advanceUntilIdle()
        
        verify(awgBoxConfigGenerator).buildConfig(
            any(), any(), any(), any(), any(), any(), 
            eq(true), // allowLan should be true
            any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any(), any(), any()
        )
    }
}
