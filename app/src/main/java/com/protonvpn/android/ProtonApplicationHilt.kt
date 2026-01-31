package com.protonvpn.android

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.hilt.work.HiltWorkerFactory
import androidx.startup.AppInitializer
import androidx.work.Configuration
import com.protonvpn.android.logging.MemoryMonitor
import com.protonvpn.android.proxy.VlessManager
import com.protonvpn.android.ui.onboarding.OnboardingTelemetry
import com.protonvpn.android.ui.promooffers.TestNotificationLoader
import com.protonvpn.android.utils.SentryIntegration
import com.protonvpn.android.utils.isMainProcess
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.proton.core.auth.presentation.MissingScopeInitializer
import me.proton.core.crypto.validator.presentation.init.CryptoValidatorInitializer
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.presentation.init.UnAuthSessionFetcherInitializer
import me.proton.core.plan.presentation.UnredeemedPurchaseInitializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject

@HiltAndroidApp
class ProtonApplicationHilt : ProtonApplication(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var onboardingTelemetry: dagger.Lazy<OnboardingTelemetry>
    @Inject lateinit var testNotificationLoader: dagger.Lazy<TestNotificationLoader>
    @Inject lateinit var updateMigration: UpdateMigration
    @Inject lateinit var memoryMonitor: dagger.Lazy<MemoryMonitor>

    @Inject
    @SharedOkHttpClient
    lateinit var okHttpClient: OkHttpClient

    private val job = SupervisorJob()
    val appScope = CoroutineScope(job + Dispatchers.IO)
    val vlessManager: VlessManager by lazy { VlessManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        SentryIntegration.initAccountSentry()

        if (isMainProcess()) {
            initDependencies()

            AppInitializer.getInstance(this).initializeComponent(CryptoValidatorInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(MissingScopeInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(UnredeemedPurchaseInitializer::class.java)
            AppInitializer.getInstance(this).initializeComponent(UnAuthSessionFetcherInitializer::class.java)

            if (BuildConfig.DEBUG) {
                WebView.setWebContentsDebuggingEnabled(true)
                testNotificationLoader.get().loadTestFile()
            }

            updateMigration.handleUpdate()
            onboardingTelemetry.get().onAppStart()
            memoryMonitor.get().start()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memoryMonitor.get().onTrimMemory()
    }
}
