/* Copyright (C) 2026 SMH01 — GPL-3.0-or-later */
package ru.protonmod.next.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.protonmod.next.R
import ru.protonmod.next.data.local.WarpPreferences
import ru.protonmod.next.data.repository.CustomConfig
import ru.protonmod.next.ui.screens.countries.CountriesListContent
import ru.protonmod.next.ui.screens.countries.CountryDisplayItem
import ru.protonmod.next.ui.theme.ProtonNextTheme

/** Uses fixture data only; never registers accounts or starts a VPN. */
@RunWith(AndroidJUnit4::class)
class WarpUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun dropdownsSelectProfilePortAndCustomEndpoint() {
        var result = WarpPreferences()
        compose.setContent { ProtonNextTheme {
            var value by remember { mutableStateOf(WarpPreferences()) }
            Column { WarpOptionsForm(value, { value = it; result = it }, true) }
        } }
        compose.onNodeWithText("WARP #2").performClick()
        compose.onNodeWithText("WARP #3").performClick()
        compose.runOnIdle { assertTrue(result.profile == 3) }
        compose.onNodeWithText("2408").performClick()
        compose.onNodeWithText("500").performClick()
        compose.runOnIdle { assertTrue(result.port == 500) }
        compose.onNodeWithText(text(R.string.warp_api_endpoint)).performClick()
        compose.onNodeWithText(text(R.string.warp_custom_endpoint)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.warp_custom_endpoint))).performTextInput("warp.example.com")
        compose.runOnIdle { assertTrue(result.customEndpoint && result.customHost == "warp.example.com") }
    }

    @Test fun countriesAreOneListWarpFirstAndCustomLastWithSideAddMenu() {
        var imported = false
        compose.setContent { ProtonNextTheme {
            CountriesListContent(countries = listOf(CountryDisplayItem("DE", 12)).toImmutableList(),
                connectedServer = null, onCountryClick = {}, onCountryMore = {},
                configs = listOf(CustomConfig(id = "mine", name = "My custom", link = "fixture"),
                    CustomConfig(id = "warp", name = "WARP", link = "fixture", kind = "warp")),
                warpEnabled = true, onAddCustom = { imported = true })
        } }
        compose.onNodeWithText("WARP", substring = false).assertIsDisplayed()
        compose.onNodeWithText("My custom", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription(text(R.string.custom_add_menu)).performClick()
        compose.onNodeWithText(text(R.string.custom_add), substring = false).performClick()
        compose.runOnIdle { assertTrue(imported) }
    }

    @Test fun chainPickerAddsStoredWarpReferenceWithoutCopyingSecret() {
        var changed = ""
        compose.setContent { ProtonNextTheme {
            ProxyChainEditor(config = "", onConfigChange = { changed = it }, warpEnabled = true,
                savedConfigs = listOf(CustomConfig(id = "warp", name = "WARP", link = "fixture-secret", kind = "warp")))
        } }
        compose.onNodeWithText(text(R.string.proxy_chain_add)).performClick()
        compose.onNodeWithText("WARP", substring = false).performClick()
        compose.runOnIdle { assertTrue(changed == "custom://warp") }
    }
}
