/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package ru.protonmod.next.ui.screens.countries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.data.network.probe.AvailabilityBadge
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * Server scanner verdict next to a country, city or server: green check - available,
 * yellow check - not every server is available, red cross - blocked, yellow dot - could not verify.
 */
@Composable
fun AvailabilityBadgeIcon(badge: AvailabilityBadge?, modifier: Modifier = Modifier) {
    if (badge == null) return
    val colors = ProtonNextTheme.colors
    val description = stringResource(
        when (badge) {
            AvailabilityBadge.ALL_AVAILABLE -> R.string.server_scan_badge_available
            AvailabilityBadge.PARTIAL -> R.string.server_scan_badge_partial
            AvailabilityBadge.ALL_BLOCKED -> R.string.server_scan_badge_blocked
            AvailabilityBadge.UNKNOWN -> R.string.server_scan_badge_unknown
        }
    )
    Box(
        modifier = modifier.size(20.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        when (badge) {
            AvailabilityBadge.ALL_AVAILABLE -> BadgeGlyph(ProtonIcons.Checkmark, colors.notificationSuccess)
            AvailabilityBadge.PARTIAL -> BadgeGlyph(ProtonIcons.Checkmark, colors.notificationWarning)
            AvailabilityBadge.ALL_BLOCKED -> BadgeGlyph(ProtonIcons.Cross, colors.notificationError)
            AvailabilityBadge.UNKNOWN -> Box(Modifier.size(10.dp).background(colors.notificationWarning, CircleShape))
        }
    }
}

@Composable
private fun BadgeGlyph(icon: ImageVector, tint: Color) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}
