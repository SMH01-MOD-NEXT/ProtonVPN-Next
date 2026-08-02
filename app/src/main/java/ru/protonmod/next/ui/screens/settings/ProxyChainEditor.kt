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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import ru.protonmod.next.R
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.vpn.ProxyLinkParser

private const val MAX_PROXY_HOPS = 4

@Composable
fun ProxyChainEditor(
    config: String,
    onConfigChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    var links by remember(config) {
        mutableStateOf(config.lineSequence().map(String::trim).filter(String::isNotEmpty).toList())
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var orderChanged by remember { mutableStateOf(false) }
    val swapThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }

    fun persist(next: List<String>) {
        links = next
        onConfigChange(next.joinToString("\n"))
    }

    fun finishDrag() {
        if (orderChanged) onConfigChange(links.joinToString("\n"))
        draggedIndex = -1
        dragOffset = 0f
        orderChanged = false
    }

    fun dragBy(deltaY: Float) {
        if (draggedIndex !in links.indices) return
        dragOffset += deltaY
        while (abs(dragOffset) >= swapThreshold) {
            val direction = if (dragOffset > 0f) 1 else -1
            val target = draggedIndex + direction
            if (target !in links.indices) {
                dragOffset = direction * swapThreshold
                break
            }
            links = links.toMutableList().apply {
                val moving = removeAt(draggedIndex)
                add(target, moving)
            }
            draggedIndex = target
            dragOffset -= direction * swapThreshold
            orderChanged = true
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCard {
            if (links.isEmpty()) {
                Text(
                    text = stringResource(R.string.proxy_chain_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }

            links.forEachIndexed { index, link ->
                val info = remember(link) { runCatching { ProxyLinkParser.inspectLink(link) }.getOrNull() }
                ProxyTreeNode(
                    index = index,
                    title = info?.name ?: stringResource(R.string.proxy_chain_invalid_node),
                    subtitle = info?.let { "${it.protocol.uppercase()}  •  ${it.server}:${it.port}" }
                        ?: stringResource(R.string.proxy_chain_invalid),
                    isDragging = draggedIndex == index,
                    dragOffset = if (draggedIndex == index) dragOffset else 0f,
                    onDelete = { persist(links.toMutableList().also { it.removeAt(index) }) },
                    onDragStart = {
                        draggedIndex = index
                        dragOffset = 0f
                        orderChanged = false
                    },
                    onDrag = ::dragBy,
                    onDragEnd = ::finishDrag
                )
            }

            ProtonDestinationNode(hasProxies = links.isNotEmpty())
        }

        OutlinedButton(
            onClick = { showAddDialog = true },
            enabled = links.size < MAX_PROXY_HOPS,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(ProtonIcons.Plus, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (links.size < MAX_PROXY_HOPS) stringResource(R.string.proxy_chain_add)
                else stringResource(R.string.proxy_chain_limit)
            )
        }

        Text(
            text = stringResource(R.string.proxy_chain_reorder_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textWeak,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    if (showAddDialog) {
        var input by remember { mutableStateOf("") }
        val normalized = input.trim()
        val info = remember(normalized) {
            normalized.takeIf(String::isNotBlank)?.let { runCatching { ProxyLinkParser.inspectLink(it) }.getOrNull() }
        }
        val isDuplicate = normalized in links

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.proxy_chain_add_title), color = colors.textNorm) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.proxy_chain_add_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text(stringResource(R.string.proxy_chain_link_label)) },
                        placeholder = { Text(stringResource(R.string.proxy_chain_placeholder)) },
                        isError = normalized.isNotEmpty() && (info == null || isDuplicate),
                        supportingText = {
                            when {
                                isDuplicate -> Text(stringResource(R.string.proxy_chain_duplicate), color = colors.notificationError)
                                normalized.isNotEmpty() && info == null -> Text(stringResource(R.string.proxy_chain_invalid), color = colors.notificationError)
                                info != null -> Text(info.name, color = colors.notificationSuccess)
                            }
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        persist(links + normalized)
                        showAddDialog = false
                    },
                    enabled = info != null && !isDuplicate && links.size < MAX_PROXY_HOPS
                ) { Text(stringResource(R.string.proxy_chain_add_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = colors.backgroundSecondary
        )
    }
}

@Composable
private fun ProxyTreeNode(
    index: Int,
    title: String,
    subtitle: String,
    isDragging: Boolean,
    dragOffset: Float,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .graphicsLayer { translationY = dragOffset }
            .zIndex(if (isDragging) 1f else 0f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChainRail(label = (index + 1).toString())
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 5.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isDragging) colors.brandNorm.copy(alpha = 0.18f)
            else colors.backgroundSecondary.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textNorm,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        ProtonIcons.Trash,
                        contentDescription = stringResource(R.string.proxy_chain_delete),
                        tint = colors.notificationError
                    )
                }
                Icon(
                    ProtonIcons.LinesHorizontal,
                    contentDescription = stringResource(R.string.proxy_chain_drag),
                    tint = colors.textWeak,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(enabled = false) {}
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                }
                            )
                        }
                        .padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun ProtonDestinationNode(hasProxies: Boolean) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (hasProxies) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight(0.5f)
                        .background(colors.brandNorm.copy(alpha = 0.45f))
                )
            }
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.brandNorm),
                contentAlignment = Alignment.Center
            ) {
                Icon(ProtonIcons.CirclesLock, contentDescription = null, tint = colors.onInteraction, modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.proxy_chain_proton_server),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm
            )
            Text(
                text = stringResource(R.string.proxy_chain_proton_server_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textWeak
            )
        }
    }
}

@Composable
private fun ChainRail(label: String) {
    val colors = ProtonNextTheme.colors
    Box(modifier = Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(colors.brandNorm.copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(colors.backgroundNorm),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = colors.brandNorm, fontWeight = FontWeight.Bold)
            }
        }
    }
}
