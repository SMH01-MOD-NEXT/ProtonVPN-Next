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

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import ru.protonmod.next.data.network.ProtonAuthApi
import ru.protonmod.next.data.network.ProtonVpnApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROTON_API_BASE_URL = "https://vpn-api.proton.me/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * IMPORTANT: We should ideally use the same OkHttpClient that supports VLESS proxy
     * if the user is in a restricted region.
     * For now, we fix the headers and rely on the manifest security config.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val headerInterceptor = Interceptor { chain ->
            val userAgent = "ProtonVPN/5.15.95.5 (Android 12; HUAWEI BLK-LX9)"
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", userAgent)
                .addHeader("x-pm-appversion", "android-vpn@5.15.95.5-dev+play")
                .addHeader("x-pm-apiversion", "4")
                .addHeader("Accept", "application/vnd.protonmail.v1+json")
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            // If you want API to follow the VLESS proxy,
            // you should inject and set the ProxySelector here as well.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = MediaType.get("application/json")
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