package ru.protonmod.next.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.serialization.json.Json
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.model.ota.UpdateInfo
import ru.protonmod.next.data.model.ota.UpdateResponse
import ru.protonmod.next.data.network.ota.UpdateApi
import ru.protonmod.next.utils.ProtonLogger
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import ru.protonmod.next.data.local.SettingsManager

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class UpdateRepository @Inject constructor(
    private val updateApi: UpdateApi,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) {
    private val updateUrls = listOf(
        context.getString(R.string.url_ota_mirror_1),
        context.getString(R.string.url_ota_mirror_2)
    )

    /**
     * Fetches and parses update metadata from the given URL.
     * Validates that the response Content-Type is JSON before attempting deserialization.
     * If the server returns HTML (e.g. a Telegram channel page from a tampered URL),
     * a descriptive exception is thrown and the full URL + response preview is sent to Sentry.
     */
    private suspend fun fetchUpdateResponse(url: String): UpdateResponse {
        val response = updateApi.getUpdateMetadata(url)
        val body = response.body()
        val contentType = response.headers()["Content-Type"] ?: ""

        if (!response.isSuccessful || body == null) {
            val errorBody = response.errorBody()?.string()?.take(200)
            Sentry.withScope { scope ->
                scope.setExtra("ota_url", url)
                scope.setExtra("http_status", response.code().toString())
                scope.setExtra("error_body_preview", errorBody ?: "(empty)")
                scope.level = SentryLevel.ERROR
                Sentry.captureMessage("OTA update fetch failed: HTTP ${response.code()} from $url")
            }
            throw HttpException(response)
        }

        if (!contentType.contains("application/json", ignoreCase = true)) {
            val bodyPreview = body.string().take(200)
            Sentry.withScope { scope ->
                scope.setExtra("ota_url", url)
                scope.setExtra("content_type", contentType)
                scope.setExtra("response_body_preview", bodyPreview)
                scope.level = SentryLevel.ERROR
                Sentry.captureMessage(
                    "OTA endpoint returned non-JSON response (Content-Type: '$contentType'). " +
                    "URL may have been tampered. URL: $url"
                )
            }
            throw IllegalStateException(
                "OTA update URL '$url' returned non-JSON content (Content-Type: '$contentType'). " +
                "Expected 'application/json'. Response starts with: ${bodyPreview.take(80)}"
            )
        }

        return json.decodeFromString(UpdateResponse.serializer(), body.string())
    }

    suspend fun getAvailableChannels(): Map<String, Boolean> {
        val result = mutableMapOf("stable" to false, "nightly" to false)
        for (url in updateUrls) {
            try {
                val urlWithCacheBuster = if (url.contains("?")) {
                    "$url&t=${System.currentTimeMillis()}"
                } else {
                    "$url?t=${System.currentTimeMillis()}"
                }
                val response = fetchUpdateResponse(urlWithCacheBuster)
                if (response.stable != null) result["stable"] = true
                if (response.nightly != null) result["nightly"] = true
                if (result["stable"] == true && result["nightly"] == true) break
            } catch (e: HttpException) {
                ProtonLogger.e("UpdateRepository", "HTTP ${e.code()} fetching channels from $url", e)
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch channels from $url", e)
            }
        }
        return result
    }

    suspend fun checkForUpdates(): UpdateInfo? {
        val selectedChannel = settingsManager.otaUpdateChannel.first()
        var bestUpdate: UpdateInfo? = null
        for (url in updateUrls) {
            try {
                val urlWithCacheBuster = if (url.contains("?")) {
                    "$url&t=${System.currentTimeMillis()}"
                } else {
                    "$url?t=${System.currentTimeMillis()}"
                }

                val response = fetchUpdateResponse(urlWithCacheBuster)
                
                val channelUpdates = if (selectedChannel == "nightly") {
                    response.nightly
                } else {
                    response.stable
                }

                val updateInfo = if (BuildConfig.DEBUG) {
                    channelUpdates?.debug
                } else {
                    channelUpdates?.release
                }
                
                if (updateInfo != null) {
                    val isHigherVersion = updateInfo.versionCode > BuildConfig.VERSION_CODE
                    
                    val isSwitchingToStable = selectedChannel == "stable" && 
                                              BuildConfig.UPDATE_CHANNEL == "nightly" && 
                                              updateInfo.versionCode == BuildConfig.VERSION_CODE

                    if (isHigherVersion || isSwitchingToStable) {
                        if (bestUpdate == null || updateInfo.versionCode > bestUpdate.versionCode) {
                            bestUpdate = updateInfo
                        }
                    }
                }
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                ProtonLogger.e("UpdateRepository", "HTTP ${e.code()} from $url: $errorBody", e)
            } catch (e: Exception) {
                ProtonLogger.e("UpdateRepository", "Failed to fetch updates from $url", e)
            }
        }
        return bestUpdate
    }
}
