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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.CertificatePinner
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.*
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.NetworkMonitor
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.vpn.AmneziaVpnManager
import ru.protonmod.next.vpn.VpnTunnelState
import java.io.IOException
import java.net.*
import java.util.concurrent.TimeUnit
import java.net.Inet4Address
import javax.inject.Provider
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROTON_PROXY_NETLIFY_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
    private const val PROTON_PROXY_CLOUDFLARE_URL = "https://api.protonnext.qzz.io/"
    private const val PROTON_PROXY_DENO_URL = "https://protonvpn-next-mirror.smh01-mirrors.deno.net/"
    private const val PROTON_DIRECT_URL = "https://vpn-api.proton.me/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun buildDnsOverHttps(bootstrapClient: OkHttpClient): DnsOverHttps {
        return DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        sessionManager: SessionManager
    ): TokenAuthenticator {
        return TokenAuthenticator(sessionManager)
    }

    /**
     * Helper function to determine if API requests should be routed through the bypass proxy.
     * Evaluates active VPN states (both app-level and OS-level) and user preferences.
     */
    private fun shouldUseApiBypass(
        context: Context,
        vpnManagerProvider: Provider<AmneziaVpnManager>,
        settingsManagerProvider: Provider<SettingsManager>
    ): Boolean {
        // 1. If our VPN tunnel is active, bypass is not needed
        // Using provider.get() here is safe because this function is called inside interceptors/DNS
        // which run on background threads, OR it's called during OkHttp init which we've made safer.
        val vpnManager = vpnManagerProvider.get()
        if (vpnManager.tunnelState.value == VpnTunnelState.UP) return false

        // 2. If a third-party VPN is active at the OS level, bypass is not needed
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return false
            }
        } catch (e: Exception) {
            // Ignore potential permission issues and fallback to reading settings
        }

        // 3. Read user preferences synchronously.
        val settingsManager = settingsManagerProvider.get()
        return settingsManager.isApiBypassEnabledSync()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        vpnManagerProvider: Provider<AmneziaVpnManager>,
        settingsManagerProvider: Provider<SettingsManager>,
        tokenAuthenticator: TokenAuthenticator,
        dohFallbackInterceptor: DohFallbackInterceptor,
        dohFallbackStore: DohFallbackStore,
        networkMonitor: NetworkMonitor
    ): OkHttpClient {
        try {
            OkHttp.initialize(context)
        } catch (e: Throwable) {}

        val protonDirectHost = PROTON_DIRECT_URL.toHttpUrl().host
        val protonNetlifyHost = PROTON_PROXY_NETLIFY_URL.toHttpUrl().host
        val protonCloudflareHost = PROTON_PROXY_CLOUDFLARE_URL.toHttpUrl().host
        val protonDenoHost = PROTON_PROXY_DENO_URL.toHttpUrl().host

        val certificatePinner = CertificatePinner.Builder()
            .apply {
                val allPins = NetworkConstants.DEFAULT_SPKI_PINS + 
                            NetworkConstants.ALTERNATIVE_API_SPKI_PINS +
                            NetworkConstants.PROXY_SPKI_PINS
                listOf(
                    "vpn-api.proton.me",
                    "api.protonmail.ch",
                    "api.protonvpn.ch",
                    "*.proton.me",
                    "*.protonmail.ch",
                    "*.protonvpn.ch",
                    "*.protonvpn.com",
                    "*.protonmail.com",
                    "*.qzz.io",
                    "*.netlify.app",
                    "*.deno.net"
                ).forEach { host ->
                    allPins.forEach { pin ->
                        add(host, "sha256/$pin")
                    }
                }
            }
            .build()

        // Interceptor to dynamically swap the base URL depending on bypass rules
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            val request = chain.request()
            val originalUrl = request.url
            val userAgent = DeviceInfoProvider.getSpoofedUserAgent()
            
            // Only rewrite if it's a Proton API request (direct or through one of the proxies)
            val host = originalUrl.host
            val isProtonApi = (host == protonDirectHost || 
                              host.endsWith(".proton.me") ||
                              host.endsWith(".protonmail.ch") ||
                              host.endsWith(".protonvpn.ch") ||
                              host.endsWith(".protonvpn.com") ||
                              host.endsWith(".protonmail.com") ||
                              host == "proton.me" ||
                              host == "protonmail.ch" ||
                              host == "protonvpn.ch" ||
                              host == "protonvpn.com" ||
                              host == "protonmail.com" ||
                              host == protonNetlifyHost ||
                              host == protonCloudflareHost ||
                              host == protonDenoHost)
            
            if (!isProtonApi) {
                // For non-Proton requests (like OTA mirrors), ensure we still provide a standard User-Agent.
                // Some hosting providers return 404 or 403 for requests without a User-Agent.
                val builder = request.newBuilder()
                if (request.header("User-Agent") == null) {
                    builder.header("User-Agent", userAgent)
                }
                // Add a generic Accept header if not present
                if (request.header("Accept") == null) {
                    builder.header("Accept", "application/json, text/plain, */*")
                }
                return@Interceptor chain.proceed(builder.build())
            }

            val spoofedVersion = DeviceInfoProvider.SPOOFED_APP_VERSION

            val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
            val settings = settingsManagerProvider.get()
            val strategy = settings.getApiBypassStrategySync()
            
            if (useProxy && (strategy == SettingsManager.STRATEGY_PROTON_MIRRORS || 
                            strategy == SettingsManager.STRATEGY_BYEDPI ||
                            strategy == SettingsManager.STRATEGY_CUSTOM_PROXY)) {
                // For Proton Mirrors strategy, we rely on DohFallbackInterceptor and dynamicDns
                // For Custom Proxy strategy, we rely on ProxySelector and use original Host.
                // No URL rewriting needed here, just proceed with original Host.
                val builder = request.newBuilder()
                    .header("User-Agent", userAgent)
                    .header("x-pm-appversion", "android-vpn@$spoofedVersion-dev+play")
                    .header("x-pm-apiversion", "4")
                    .header("Accept", "application/vnd.protonmail.v1+json")
                    .apply {
                        if (settings.isSpoofCountryEnabledSync()) {
                            if (!settings.isSpoofCountryNullSync()) {
                                val code = settings.getSpoofCountryCodeSync().uppercase()
                                if (code.length == 2) header("x-pm-country", code)
                            }
                        }
                    }
                
                // Ensure correct Host header is set
                builder.header("Host", originalUrl.host)
                
                return@Interceptor chain.proceed(builder.build())
            }

            val proxyBaseUrl = when (strategy) {
                SettingsManager.STRATEGY_CLOUDFLARE -> PROTON_PROXY_CLOUDFLARE_URL
                SettingsManager.STRATEGY_DENO -> PROTON_PROXY_DENO_URL
                else -> PROTON_PROXY_NETLIFY_URL
            }

            val newBaseUrl = if (useProxy) proxyBaseUrl.toHttpUrl() else PROTON_DIRECT_URL.toHttpUrl()

            val newUrl = originalUrl.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .apply {
                    if (useProxy && (strategy == SettingsManager.STRATEGY_CLOUDFLARE || 
                                    strategy == SettingsManager.STRATEGY_DENO ||
                                    strategy == SettingsManager.STRATEGY_NETLIFY)) {
                        val pathPrefix = when (originalUrl.host) {
                            "verify-api.proton.me" -> "verify-api"
                            "verify.proton.me" -> "verify"
                            else -> null
                        }
                        if (pathPrefix != null) {
                            val originalSegments = originalUrl.pathSegments
                            encodedPath("/")
                            addPathSegment(pathPrefix)
                            originalSegments.forEach { addPathSegment(it) }
                        }
                    }
                }
                .build()

            val newRequest = request.newBuilder()
                .url(newUrl)
                .header("User-Agent", userAgent)
                .header("x-pm-appversion", "android-vpn@$spoofedVersion-dev+play")
                .header("x-pm-apiversion", "4")
                .header("Accept", "application/vnd.protonmail.v1+json")
                .apply {
                    val settings = settingsManagerProvider.get()
                    if (settings.isSpoofCountryEnabledSync()) {
                        if (settings.isSpoofCountryNullSync()) {
                            // Null spoofing means no x-pm-country header is sent.
                            // Some versions of the backend may fallback to IP-based detection.
                        } else {
                            val code = settings.getSpoofCountryCodeSync().uppercase()
                            if (code.length == 2) {
                                header("x-pm-country", code)
                            }
                        }
                    }
                }
                .build()

            try {
                chain.proceed(newRequest)
            } catch (e: Exception) {
                // Log network errors for debugging lifecycle issues
                if (e is SocketTimeoutException || e is ConnectException) {
                    ProtonLogger.w("NetworkModule", "Network timeout during ${newRequest.url}: ${e.message}")
                }
                throw e
            }
        }

        // Bootstrap client for DNS over HTTPS requires longer timeouts
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val doh = buildDnsOverHttps(bootstrapClient)

        val trustManager = MirrorTrustManager()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)

        val hostnameVerifier = HostnameVerifier { hostname, session ->
            val standardVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (standardVerifier.verify(hostname, session)) return@HostnameVerifier true

            // If standard verification fails (likely because of IP or decoy domain),
            // we check if the certificate is one we trust via pinning.
            val allPins = NetworkConstants.DEFAULT_SPKI_PINS + 
                        NetworkConstants.ALTERNATIVE_API_SPKI_PINS +
                        NetworkConstants.PROXY_SPKI_PINS
            val isIp = hostname.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))
            
            if (isIp || hostname.endsWith(".qzz.io") || hostname.endsWith(".netlify.app") || 
                hostname.endsWith(".deno.net") ||
                hostname.endsWith(".proton.me") || hostname.endsWith(".protonmail.ch") ||
                hostname.endsWith(".protonvpn.ch") || hostname.endsWith(".protonvpn.com") ||
                hostname.endsWith(".protonmail.com")) {
                 return@HostnameVerifier PinVerifier.check(session, allPins)
            }

            false
        }

        // Dynamic DNS configuration
        val dynamicDns = Dns { hostname ->
            val result = mutableListOf<InetAddress>()
            
            // Check DoH Fallback Store first
            val fallbackIps = dohFallbackStore.getFallbackIps(hostname)
            if (!fallbackIps.isNullOrEmpty()) {
                ProtonLogger.i("NetworkManager", "Using fallback IPs from DoH store for $hostname")
                result.addAll(fallbackIps)
            } else {
                val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
                val settings = settingsManagerProvider.get()
                val strategy = settings.getApiBypassStrategySync()

                if (useProxy && strategy == SettingsManager.STRATEGY_CUSTOM_PROXY &&
                    settings.getApiProxyTypeSync() == SettingsManager.PROXY_TYPE_SOCKS) {
                    val proxyHost = settings.getApiProxyHostSync()
                    if (hostname != proxyHost) {
                        // Return a dummy IP address for target hosts. The custom Socks5Socket
                        // will perform remote DNS resolution on the SOCKS5 proxy server.
                        // We must resolve the proxy host itself normally.
                        ProtonLogger.i("NetworkManager", "Using dummy IP for target host: $hostname (custom SOCKS5 proxy active)")
                        return@Dns listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
                    }
                }

                if (useProxy) {
                    try {
                        result.addAll(doh.lookup(hostname))
                    } catch (e: Exception) {
                        result.addAll(Dns.SYSTEM.lookup(hostname))
                    }
                } else {
                    try {
                        // Try system DNS first
                        result.addAll(Dns.SYSTEM.lookup(hostname))
                    } catch (e: Exception) {
                        // Fallback to DoH if system DNS fails (helps bypass some blocks)
                        try {
                            result.addAll(doh.lookup(hostname))
                        } catch (ignore: Exception) {
                            throw e // Throw original exception if both fail
                        }
                    }
                }
            }

            // Prefer IPv4 over IPv6 to avoid ENETUNREACH on IPv4-only networks.
            // OkHttp connects in the order addresses are returned, so placing IPv4
            // first ensures it is tried before any IPv6 address.
            result.sortWith(compareBy { if (it is Inet4Address) 0 else 1 })

            // Log the resolve result for debugging connectivity issues in restricted regions
            ProtonLogger.i("NetworkManager", "Resolved $hostname to: ${result.joinToString(", ") { it.hostAddress ?: "unknown" }}")
            
            result
        }

        // Custom Proxy Selector
        val proxySelector = object : ProxySelector() {
            override fun select(uri: URI?): MutableList<Proxy> {
                val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
                val settings = settingsManagerProvider.get()
                val strategy = settings.getApiBypassStrategySync()

                if (useProxy) {
                    if (strategy == SettingsManager.STRATEGY_BYEDPI) {
                        val host = "127.0.0.1"
                        val port = settings.getApiProxyPortSync()
                        try {
                            val address = InetSocketAddress.createUnresolved(host, port)
                            return mutableListOf(Proxy(Proxy.Type.SOCKS, address))
                        } catch (e: Exception) {
                            ProtonLogger.e("NetworkModule", "Failed to create proxy address: $host:$port", e)
                        }
                    } else if (strategy == SettingsManager.STRATEGY_CUSTOM_PROXY) {
                        val type = settings.getApiProxyTypeSync()
                        if (type == SettingsManager.PROXY_TYPE_HTTP) {
                            val host = settings.getApiProxyHostSync()
                            val port = settings.getApiProxyPortSync()
                            if (host.isNotEmpty()) {
                                try {
                                    val address = InetSocketAddress.createUnresolved(host, port)
                                    return mutableListOf(Proxy(Proxy.Type.HTTP, address))
                                } catch (e: Exception) {
                                    ProtonLogger.e("NetworkModule", "Failed to create proxy address: $host:$port", e)
                                }
                            }
                        } else {
                            // SOCKS custom proxy is handled via custom SocketFactory, not ProxySelector
                            return mutableListOf(Proxy.NO_PROXY)
                        }
                    }
                }
                return mutableListOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                ProtonLogger.e("NetworkModule", "Proxy connection failed for $uri: ${ioe?.message}")
            }
        }

        val proxyAuthenticator = object : Authenticator {
            override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
                val settings = settingsManagerProvider.get()
                val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
                val strategy = settings.getApiBypassStrategySync()

                if (useProxy && strategy == SettingsManager.STRATEGY_CUSTOM_PROXY) {
                    val username = settings.getApiProxyUsernameSync()
                    val password = settings.getApiProxyPasswordSync()

                    if (username.isNotEmpty()) {
                        val credential = Credentials.basic(username, password)
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                }
                return null
            }
        }

        val apiBypassSocketFactory = ApiBypassSocketFactory(
            context,
            vpnManagerProvider,
            settingsManagerProvider,
            ::shouldUseApiBypass
        )

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .addInterceptor(dohFallbackInterceptor)
            .addInterceptor(AuthLoggingInterceptor())
            .authenticator(tokenAuthenticator)
            .proxyAuthenticator(proxyAuthenticator)
            .dns(dynamicDns)
            .proxySelector(proxySelector)
            .socketFactory(apiBypassSocketFactory)
            .certificatePinner(certificatePinner)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            // Connect timeout increased to 30s to allow tunnel stabilization
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // The base URL provided here is just a placeholder, the interceptor rewrites it
            .baseUrl(PROTON_DIRECT_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideProtonAuthApi(retrofit: Retrofit): ProtonAuthApi = retrofit.create(ProtonAuthApi::class.java)


    @Provides
    @Singleton
    fun provideProtonVpnApi(retrofit: Retrofit): ProtonVpnApi = retrofit.create(ProtonVpnApi::class.java)

    @Provides
    @Singleton
    fun provideUpdateApi(retrofit: Retrofit): UpdateApi = retrofit.create(UpdateApi::class.java)
}