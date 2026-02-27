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

import android.os.Build
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Retrofit
import ru.protonmod.next.data.network.ProtonAuthApi
import ru.protonmod.next.data.network.ProtonVpnApi
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Pointing to the deployed Netlify proxy.
    private const val PROTON_API_BASE_URL = "https://shimmering-stroopwafel-51675e.netlify.app/"
    private const val APP_VERSION_STRING = "5.15.95.5"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Helper function to build a safe, device-specific User-Agent.
     * Proton API is very strict, so we maintain the exact structure of the official app.
     */
    private fun generateUserAgent(): String {
        val androidVersion = Build.VERSION.RELEASE ?: "12"
        val manufacturer = Build.MANUFACTURER ?: "Unknown"
        val model = Build.MODEL ?: "Device"

        // Capitalize manufacturer to look natural (e.g., "samsung" -> "Samsung")
        val capManufacturer = manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }

        // Sometimes the model already starts with the manufacturer name (e.g. "HTC One")
        val deviceName = if (model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        } else {
            "$capManufacturer $model"
        }

        // Strip any non-ASCII characters.
        // Some obscure custom ROMs include emojis or unicode characters in the device name.
        // OkHttp will crash if it tries to add non-ASCII characters to HTTP headers.
        val safeDeviceName = deviceName.replace(Regex("[^\\x20-\\x7E]"), "").trim()
        val safeAndroidVersion = androidVersion.replace(Regex("[^\\x20-\\x7E]"), "").trim()

        return "ProtonVPN/$APP_VERSION_STRING (Android $safeAndroidVersion; $safeDeviceName)"
    }

    /**
     * Creates a DNS over HTTPS (DoH) client to bypass local DNS poisoning.
     * This ensures that even if the ISP tries to block the worker URL via DNS,
     * the app will resolve it securely via Cloudflare's 1.1.1.1.
     */
    private fun buildDnsOverHttps(bootstrapClient: OkHttpClient): DnsOverHttps {
        return DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            // Bootstrap IPs for Cloudflare DNS so it doesn't need DNS to find the DoH server
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
    fun provideOkHttpClient(): OkHttpClient {
        val headerInterceptor = Interceptor { chain ->
            val userAgent = generateUserAgent()

            val request = chain.request().newBuilder()
                .addHeader("User-Agent", userAgent)
                .addHeader("x-pm-appversion", "android-vpn@$APP_VERSION_STRING-dev+play")
                .addHeader("x-pm-apiversion", "4")
                .addHeader("Accept", "application/vnd.protonmail.v1+json")
                .build()
            chain.proceed(request)
        }

        // We create a basic client first to use for the DoH connection
        val bootstrapClient = OkHttpClient.Builder().build()
        val dns = buildDnsOverHttps(bootstrapClient)

        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .dns(dns) // Apply secure DNS to bypass local restrictions
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PROTON_API_BASE_URL)
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