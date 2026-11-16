package ru.protonmod.next.data.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetiredBypassInstrumentedTest {
    @Test
    fun oldNetlifyPreferenceAndNewWritesUseCloudflare() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("boot_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("api_bypass_strategy", SettingsManager.STRATEGY_NETLIFY).commit()
        val settings = SettingsManager(context)
        assertEquals(SettingsManager.STRATEGY_CLOUDFLARE, settings.getApiBypassStrategySync())
        settings.setApiBypassStrategy(SettingsManager.STRATEGY_NETLIFY)
        assertEquals(SettingsManager.STRATEGY_CLOUDFLARE, settings.getApiBypassStrategySync())
        assertEquals(SettingsManager.STRATEGY_CLOUDFLARE, settings.apiBypassStrategy.first())
        settings.setApiBypassStrategy(SettingsManager.STRATEGY_EVENT)
        assertEquals(SettingsManager.STRATEGY_EVENT, settings.getApiBypassStrategySync())
        assertEquals(SettingsManager.STRATEGY_EVENT, settings.apiBypassStrategy.first())
    }
}
