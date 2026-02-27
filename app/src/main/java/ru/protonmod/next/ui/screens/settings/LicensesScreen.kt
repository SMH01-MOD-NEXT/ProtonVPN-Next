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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

data class LicenseItem(
    val titleRes: Int,
    val licenseRes: Int,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(
    onBack: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val licenses = listOf(
        LicenseItem(R.string.license_app_title, R.string.license_app_desc, "https://www.gnu.org/licenses/gpl-3.0.html"),
        LicenseItem(R.string.license_androidx_title, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseItem(R.string.license_compose_title, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseItem(R.string.license_kotlin_title, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseItem(R.string.license_hilt_title, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseItem(R.string.license_retrofit_title, R.string.license_apache_2, "https://www.apache.org/licenses/LICENSE-2.0"),
        LicenseItem(R.string.license_proton_go_vpn_title, R.string.license_app_desc, "https://github.com/ProtonMail/gopenpgp"),
        LicenseItem(R.string.license_amneziawg_title, R.string.license_apache_2, "https://github.com/amnezia-vpn/amneziawg-android")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.licenses_title),
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.2f),
                                colors.backgroundNorm
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(licenses) { item ->
                    LicenseCard(item)
                }
            }
        }
    }
}

@Composable
fun LicenseCard(item: LicenseItem) {
    val colors = ProtonNextTheme.colors
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundSecondary.copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textNorm
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(item.licenseRes),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textWeak
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.url,
                style = MaterialTheme.typography.bodySmall,
                color = colors.brandNorm
            )
        }
    }
}
