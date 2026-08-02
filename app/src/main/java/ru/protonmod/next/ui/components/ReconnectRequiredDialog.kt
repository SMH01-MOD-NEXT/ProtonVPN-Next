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

package ru.protonmod.next.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme

/**
 * Notice shown when connection settings are changed while the tunnel is up. Changing them does not
 * touch the running tunnel, so the user is told that a reconnect is what applies them.
 *
 * @param canReconnect whether the current tunnel can be restarted from here.
 */
@Composable
fun ReconnectRequiredDialog(
    canReconnect: Boolean,
    onPostpone: () -> Unit,
    onReconnect: () -> Unit,
    onDisablePrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var dontShowAgain by remember { mutableStateOf(false) }

    fun close(reconnect: Boolean) {
        if (dontShowAgain) onDisablePrompt()
        when {
            reconnect -> onReconnect()
            !dontShowAgain -> onPostpone()
        }
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = { close(reconnect = false) },
        icon = {
            Icon(
                imageVector = ProtonIcons.ArrowsRotate,
                contentDescription = null,
                tint = colors.brandNorm
            )
        },
        title = { Text(stringResource(R.string.reconnect_required_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (canReconnect) {
                            R.string.reconnect_required_message
                        } else {
                            R.string.reconnect_required_message_manual
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.brandNorm,
                            uncheckedColor = colors.textWeak,
                            checkmarkColor = colors.textInverted
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.reconnect_required_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textNorm
                    )
                }
            }
        },
        confirmButton = {
            if (canReconnect) {
                TextButton(onClick = { close(reconnect = true) }) {
                    Text(stringResource(R.string.reconnect_required_action))
                }
            } else {
                TextButton(onClick = { close(reconnect = false) }) {
                    Text(stringResource(R.string.reconnect_required_understood))
                }
            }
        },
        dismissButton = {
            if (canReconnect) {
                TextButton(onClick = { close(reconnect = false) }) {
                    Text(stringResource(R.string.reconnect_required_later))
                }
            }
        },
        containerColor = colors.backgroundSecondary,
        titleContentColor = colors.textNorm,
        textContentColor = colors.textWeak,
        iconContentColor = colors.brandNorm
    )
}
