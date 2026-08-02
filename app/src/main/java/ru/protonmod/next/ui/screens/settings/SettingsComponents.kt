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

package ru.protonmod.next.ui.screens.settings

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@Composable
fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: (@Composable ColumnScope.() -> Unit),
) {
    val colors = ProtonNextTheme.colors
    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm,
                modifier = Modifier
                    .padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
                    .fillMaxWidth()
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = RoundedCornerShape(20.dp), alpha = 0.4f, shadowElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingRowWithIcon(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    iconTint: Boolean = true,
    subtitle: String? = null,
    enabled: Boolean = true,
    titleColor: Color = ProtonNextTheme.colors.textNorm,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    var baseModifier = modifier.fillMaxWidth()

    // Pass the enabled state to the clickable modifier
    if (onClick != null) {
        baseModifier = baseModifier.clickable(
            enabled = enabled,
            onClick = onClick
        )
    }

    // Apply visual opacity when disabled
    baseModifier = baseModifier
        .alpha(if (enabled) 1f else 0.5f)
        .padding(vertical = 12.dp, horizontal = 16.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null || iconRes != null) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.brandNorm.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                val iconColor = if (titleColor != colors.textNorm) titleColor else colors.brandNorm
                if (iconRes != null) {
                    // Proton VPN ships some feature icons as pre-colored assets and
                    // draws them untinted, the same way the official client does.
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = if (iconTint) iconColor else Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon!!,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(
                imageVector = ProtonIcons.ChevronRight,
                contentDescription = null,
                tint = colors.iconWeak.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    iconTint: Boolean = true,
    enabled: Boolean = true
) {
    val colors = ProtonNextTheme.colors
    SettingRowWithIcon(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconRes = iconRes,
        iconTint = iconTint,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled, // Pass enabled state to the Switch component
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.textInverted,
                    checkedTrackColor = colors.brandNorm,
                    uncheckedThumbColor = colors.shade60,
                    uncheckedTrackColor = colors.shade20,
                    uncheckedBorderColor = Color.Transparent,
                )
            )
        }
    )
}

@Composable
fun SentryPoweredBy(modifier: Modifier = Modifier) {
    if (!BuildConfig.SENTRY_ENABLED) return
    
    val context = LocalContext.current
    val url = stringResource(R.string.url_sentry)
    val colors = ProtonNextTheme.colors
    val sentryLogo = if (colors.isDark) R.drawable.sentry_light else R.drawable.sentry_dark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            }
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.about_sentry),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textWeak
        )
        Spacer(modifier = Modifier.height(8.dp))
        Icon(
            painter = painterResource(id = sentryLogo),
            contentDescription = "Sentry",
            tint = Color.Unspecified,
            modifier = Modifier.height(24.dp)
        )
    }
}
