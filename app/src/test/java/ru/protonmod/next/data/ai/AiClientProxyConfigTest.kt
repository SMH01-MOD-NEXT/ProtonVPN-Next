/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.protonmod.next.data.local.SettingsManager

class AiClientProxyConfigTest {
    @Test
    fun `bypass disabled uses normal VPN or system transport`() {
        assertNull(
            selectAiProxyConfig(
                useBypass = false,
                strategy = SettingsManager.STRATEGY_CUSTOM_PROXY,
                type = SettingsManager.PROXY_TYPE_SOCKS,
                host = "proxy.example",
                port = 1080,
                username = "",
                password = ""
            )
        )
    }

    @Test
    fun `DNS and mirror strategies cannot claim to bypass regional restrictions`() {
        assertNull(
            selectAiProxyConfig(
                useBypass = true,
                strategy = SettingsManager.STRATEGY_PROTON_MIRRORS,
                type = SettingsManager.PROXY_TYPE_SOCKS,
                host = "proxy.example",
                port = 1080,
                username = "",
                password = ""
            )
        )
    }

    @Test
    fun `custom SOCKS proxy is selected with remote DNS credentials`() {
        assertEquals(
            AiProxyConfig(
                type = SettingsManager.PROXY_TYPE_SOCKS,
                host = "proxy.example",
                port = 1080,
                username = "user",
                password = "secret"
            ),
            selectAiProxyConfig(
                useBypass = true,
                strategy = SettingsManager.STRATEGY_CUSTOM_PROXY,
                type = SettingsManager.PROXY_TYPE_SOCKS,
                host = " proxy.example ",
                port = 1080,
                username = "user",
                password = "secret"
            )
        )
    }

    @Test
    fun `invalid custom proxy falls back to normal transport`() {
        assertNull(
            selectAiProxyConfig(
                useBypass = true,
                strategy = SettingsManager.STRATEGY_CUSTOM_PROXY,
                type = SettingsManager.PROXY_TYPE_HTTP,
                host = "",
                port = 70000,
                username = "",
                password = ""
            )
        )
    }
}
