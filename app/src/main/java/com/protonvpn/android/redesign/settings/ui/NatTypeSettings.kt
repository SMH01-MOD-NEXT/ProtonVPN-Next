/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.settings.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak

class TestCrash : RuntimeException("Test exception, everything's fine")

@Composable
fun NatTypeSettings(
    onClose: () -> Unit,
    nat: SettingsViewModel.SettingViewState.Nat,
    onNatTypeChange: (NatType) -> Unit,
) {
    SubSetting(
        title = stringResource(id = nat.titleRes),
        onClose = onClose
    ) {
        // Описание с секретным тапом для краш-теста
        Text(
            modifier = Modifier
                .detectMultiTap(7) { throw TestCrash() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(id = R.string.settings_advanced_nat_type_description_no_learn),
            style = ProtonTheme.typography.defaultWeak,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = ProtonTheme.colors.separatorNorm
        )

        // Список опций в новом стиле
        NatType.entries.forEach { type ->
            NatTypeOption(
                type = type,
                isSelected = nat.value == type,
                onClick = { onNatTypeChange(type) }
            )
        }
    }
}

@Composable
private fun NatTypeOption(
    type: NatType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(id = type.labelRes),
                style = ProtonTheme.typography.defaultNorm,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(id = type.descriptionRes),
                style = ProtonTheme.typography.defaultWeak,
                color = ProtonTheme.colors.textWeak
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = null, // Обработка клика на Row
            colors = RadioButtonDefaults.colors(
                selectedColor = ProtonTheme.colors.interactionNorm,
                unselectedColor = ProtonTheme.colors.iconWeak
            ),
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun Modifier.detectMultiTap(count: Int, onTriggered: () -> Unit) =
    this.pointerInput(Unit) {
        var consecutiveTaps = 0
        var lastTapTimestampMs = 0L
        detectTapGestures {
            val now = SystemClock.elapsedRealtime()
            val timeSincePreviousClick = now - lastTapTimestampMs
            lastTapTimestampMs = now
            if (timeSincePreviousClick < 500) {
                ++consecutiveTaps
                if (consecutiveTaps == count) {
                    consecutiveTaps = 0
                    onTriggered()
                }
            } else {
                consecutiveTaps = 0
            }
        }
    }