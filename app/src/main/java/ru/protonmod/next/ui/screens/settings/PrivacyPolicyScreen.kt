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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.isTablet

@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    val resources = LocalResources.current
    val isTablet = isTablet()
    val policyText = remember {
        try {
            resources.openRawResource(R.raw.privacy_policy)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error loading privacy policy"
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.backgroundNorm.copy(alpha = 0.1f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            val contentModifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth()

            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    item(contentType = "Header") {
                        NavigationHeader(
                            title = stringResource(R.string.settings_privacy_policy),
                            onBack = onBack
                        )
                    }

                    item(contentType = "Header") {
                        Column(
                            modifier = contentModifier,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Icon
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(CircleShape)
                                        .background(colors.brandNorm.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ProtonIcons.ShieldHalfFilled,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = colors.brandNorm
                                    )
                                }
                            }

                            // Title
                            Text(
                                text = stringResource(R.string.settings_privacy_policy),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textNorm,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    val lines = policyText.split("\n")
                    itemsIndexed(
                        items = lines,
                        key = { index, _ -> index },
                        contentType = { _, _ -> "PolicyLine" }
                    ) { _, line ->
                        MarkdownLine(
                            line = line,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    modifier: Modifier = Modifier
) {
    val colors = ProtonNextTheme.colors
    when {
        line.startsWith("# ") -> {
            Text(
                text = line.substring(2),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = modifier.padding(vertical = 12.dp)
            )
        }
        line.startsWith("## ") -> {
            Text(
                text = line.substring(3),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = modifier.padding(vertical = 8.dp)
            )
        }
        line.startsWith("### ") -> {
            Text(
                text = line.substring(4),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = modifier.padding(vertical = 4.dp)
            )
        }
        line.startsWith("#### ") -> {
            Text(
                text = line.substring(5),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textNorm,
                fontWeight = FontWeight.Bold,
                modifier = modifier
            )
        }
        line.trim().isEmpty() -> {
            Spacer(modifier = Modifier.height(4.dp))
        }
        else -> {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textWeak,
                modifier = modifier
            )
        }
    }
}
