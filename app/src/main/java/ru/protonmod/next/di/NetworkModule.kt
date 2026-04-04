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
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import ru.protonmod.next.data.local.SessionDao
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.data.network.ProtonAuthApi
import ru.protonmod.next.data.network.ProtonVpnApi
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.data.network.TokenAuthenticator
import ru.protonmod.next.data.repository.AuthRepository
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.vpn.AmneziaVpnManager
import org.amnezia.awg.backend.Tunnel
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROTON_PROXY_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
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
        sessionDao: SessionDao,
        authRepositoryProvider: Provider<AuthRepository>
    ): TokenAuthenticator {
        return TokenAuthenticator(sessionDao, authRepositoryProvider)
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
        if (vpnManager.tunnelState.value == Tunnel.State.UP) return false

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
        val isBypassEnabled = settingsManager.isApiBypassEnabledSync()
        val strategy = settingsManager.getApiBypassStrategySync()
        
        return isBypassEnabled && strategy == "netlify"
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        vpnManagerProvider: Provider<AmneziaVpnManager>,
        settingsManagerProvider: Provider<SettingsManager>,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        try {
            OkHttp.initialize(context)
        } catch (e: Throwable) {}

        // Interceptor to dynamically swap the base URL depending on bypass rules
        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            val userAgent = DeviceInfoProvider.getSpoofedUserAgent()
            val spoofedVersion = DeviceInfoProvider.SPOOFED_APP_VERSION

            val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
            val newBaseUrl = if (useProxy) PROTON_PROXY_URL.toHttpUrl() else PROTON_DIRECT_URL.toHttpUrl()

            val newUrl = request.url.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .build()

            request = request.newBuilder()
                .url(newUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("x-pm-appversion", "android-vpn@$spoofedVersion-dev+play")
                .addHeader("x-pm-apiversion", "4")
                .addHeader("Accept", "application/vnd.protonmail.v1+json")
                .build()

            try {
                chain.proceed(request)
            } catch (e: Exception) {
                // Log network errors for debugging lifecycle issues
                if (e is java.net.SocketTimeoutException || e is java.net.ConnectException) {
                    android.util.Log.w("NetworkModule", "Network timeout during ${request.url}: ${e.message}")
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

        // Dynamic DNS configuration
        val dynamicDns = Dns { hostname ->
            val useProxy = shouldUseApiBypass(context, vpnManagerProvider, settingsManagerProvider)
            if (useProxy) {
                try {
                    doh.lookup(hostname)
                } catch (e: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            } else {
                Dns.SYSTEM.lookup(hostname)
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .authenticator(tokenAuthenticator)
            .dns(dynamicDns)
            // Reduced timeouts to detect network failures faster and prevent JNI reference leaks
            // Original: 30s connect timeout. On mobile, 15s is more responsive and safer.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
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