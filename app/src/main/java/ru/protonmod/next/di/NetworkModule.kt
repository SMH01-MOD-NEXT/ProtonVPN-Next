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
import android.os.Build
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
import ru.protonmod.next.data.network.ProtonAuthApi
import ru.protonmod.next.data.network.ProtonVpnApi
import ru.protonmod.next.data.network.TokenAuthenticator
import ru.protonmod.next.vpn.AmneziaVpnManager
import org.amnezia.awg.backend.Tunnel
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROTON_PROXY_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
    private const val PROTON_DIRECT_URL = "https://vpn-api.proton.me/"
    private const val APP_VERSION_STRING = "5.16.31.0"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun generateUserAgent(): String {
        val androidVersion = Build.VERSION.RELEASE ?: "12"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Device"
        val capManufacturer = manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        val deviceName = if (model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } else {
            "$capManufacturer $model"
        }
        val safeDeviceName = deviceName.replace(Regex("[^\\x20-\\x7E]"), "").trim()
        val safeAndroidVersion = androidVersion.replace(Regex("[^\\x20-\\x7E]"), "").trim()
        return "ProtonVPN/$APP_VERSION_STRING (Android $safeAndroidVersion; $safeDeviceName)"
    }

    private fun buildDnsOverHttps(bootstrapClient: OkHttpClient): DnsOverHttps {
        return DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
                InetAddress.getByName("2606:4700:4700::1111"),
                InetAddress.getByName("2606:4700:4700::1001")
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        sessionDao: SessionDao,
        authApiProvider: javax.inject.Provider<ProtonAuthApi>
    ): TokenAuthenticator {
        return TokenAuthenticator(sessionDao, authApiProvider)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        vpnManager: AmneziaVpnManager,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        try {
            OkHttp.initialize(context)
        } catch (e: Throwable) {}

        val dynamicBaseUrlInterceptor = Interceptor { chain ->
            var request = chain.request()
            val userAgent = generateUserAgent()

            // Determine which base URL to use
            val isVpnUp = vpnManager.tunnelState.value == Tunnel.State.UP
            val newBaseUrl = if (isVpnUp) PROTON_DIRECT_URL.toHttpUrl() else PROTON_PROXY_URL.toHttpUrl()

            val newUrl = request.url.newBuilder()
                .scheme(newBaseUrl.scheme)
                .host(newBaseUrl.host)
                .port(newBaseUrl.port)
                .build()

            request = request.newBuilder()
                .url(newUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("x-pm-appversion", "android-vpn@$APP_VERSION_STRING-dev+play")
                .addHeader("x-pm-apiversion", "4")
                .addHeader("Accept", "application/vnd.protonmail.v1+json")
                .build()

            chain.proceed(request)
        }

        val bootstrapClient = OkHttpClient.Builder().build()
        val doh = buildDnsOverHttps(bootstrapClient)

        // Dynamic DNS: Disable DoH when VPN is active
        val dynamicDns = Dns { hostname ->
            val isVpnUp = vpnManager.tunnelState.value == Tunnel.State.UP
            if (isVpnUp) {
                // Use system DNS when VPN is UP to respect tunnel DNS settings
                Dns.SYSTEM.lookup(hostname)
            } else {
                // Use DoH when VPN is DOWN to circumvent censorship
                try {
                    doh.lookup(hostname)
                } catch (e: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(dynamicBaseUrlInterceptor)
            .authenticator(tokenAuthenticator)
            .dns(dynamicDns)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PROTON_PROXY_URL)
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
}