package ru.protonmod.next.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CertSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: CertSettingsViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.backgroundNorm,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                NavigationHeader(
                    title = stringResource(R.string.settings_cert_management),
                    onBack = onBack
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
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
                            imageVector = ProtonIcons.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.brandNorm
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.settings_cert_management),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.textNorm,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.settings_cert_management_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textWeak,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .liquidGlass(shape = RoundedCornerShape(16.dp), alpha = 0.4f, shadowElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_extended_cert),
                            subtitle = if (uiState.isExtendedCertEnabled) {
                                stringResource(R.string.settings_extended_cert_enabled)
                            } else {
                                stringResource(R.string.settings_extended_cert_disabled)
                            },
                            icon = ProtonIcons.Shield,
                            checked = uiState.isExtendedCertEnabled,
                            onCheckedChange = { viewModel.setExtendedCertEnabled(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        // Certificate Status Info
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_cert_current_header),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textWeak,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            CertInfoRow(stringResource(R.string.settings_cert_id), uiState.certId)
                            uiState.certIssued?.let { CertInfoRow(stringResource(R.string.settings_cert_issued), it) }
                            uiState.certExpires?.let { CertInfoRow(stringResource(R.string.settings_cert_expires), it) }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = colors.separatorNorm.copy(alpha = 0.5f)
                        )

                        SettingRowWithIcon(
                            icon = ProtonIcons.ArrowsRotate,
                            title = stringResource(R.string.debug_btn_refresh_cert),
                            subtitle = if (uiState.isLoading) stringResource(R.string.settings_cert_loading) else stringResource(R.string.settings_cert_refresh_desc),
                            onClick = { viewModel.forceRefreshCertificate() }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = uiState.errorLog != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Red.copy(alpha = 0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.errorLog ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CertInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ProtonNextTheme.colors.textWeak
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ProtonNextTheme.colors.textNorm,
            fontWeight = FontWeight.Medium
        )
    }
}
