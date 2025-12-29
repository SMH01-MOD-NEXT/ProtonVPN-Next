/*
 * Copyright (c) 2025 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.base.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak

@Composable
fun SettingsCheckbox(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = value,
                onValueChange = onValueChange,
                role = Role.Checkbox
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = value,
            colors = CheckboxDefaults.colors(
                checkedColor = ProtonTheme.colors.interactionNorm,
                uncheckedColor = ProtonTheme.colors.iconWeak,
                checkmarkColor = Color.White // Исправлено здесь
            ),
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Column(
            Modifier.padding(start = 16.dp)
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.defaultNorm
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 2.dp),
                style = ProtonTheme.typography.defaultWeak,
                color = ProtonTheme.colors.textWeak
            )
        }
    }
}

@Preview
@Composable
fun PreviewProtonDialogCheckbox() {
    ProtonVpnPreview {
        SettingsCheckbox(
            title = "Allow direct connections",
            description = "Allow direct peer-to-peer connections over LAN.",
            value = true,
            onValueChange = {}
        )
    }
}