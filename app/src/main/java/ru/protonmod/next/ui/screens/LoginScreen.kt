/*
 * Copyright (c) 2024 Proton Technologies AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
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
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.NavigationHeader
import ru.protonmod.next.ui.components.SmoothOutlinedTextField
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.utils.isTablet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isApiBypassEnabled by viewModel.isApiBypassEnabled.collectAsStateWithLifecycle()
    val apiBypassStrategy by viewModel.apiBypassStrategy.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors
    val isTablet = isTablet()

    // Form states
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var totpCode by remember { mutableStateOf("") }
    
    var showTokenLoginDialog by remember { mutableStateOf(false) }
    var sessionJson by remember { mutableStateOf("") }

    val checkVpnAndLogin: () -> Unit = {
        viewModel.login(username, password)
    }

    Box(modifier = modifier) {
        LaunchedEffect(uiState, onLoginSuccess) {
            if (uiState is LoginUiState.Success) {
                onLoginSuccess()
            }
        }

        AnimatedContent(
            targetState = uiState,
            label = "login_transitions",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                is LoginUiState.RequiresCaptcha -> {
                    key(state.nonce) {
                        CaptchaScreen(
                            webUrl = state.webUrl,
                            sessionId = state.sessionId,
                            isApiBypassEnabled = isApiBypassEnabled,
                            apiBypassStrategy = apiBypassStrategy,
                            okHttpClient = viewModel.okHttpClient,
                            onDismiss = { viewModel.resetError() },
                            onCaptchaSolve = { verifiedToken ->
                                viewModel.retryWithCaptcha(state, verifiedToken)
                            }
                        )
                    }
                }

                is LoginUiState.Requires2FA -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = colors.backgroundNorm,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .statusBarsPadding()
                                    .widthIn(max = 480.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                NavigationHeader(title = "", onBack = onBackClick)

                                Spacer(modifier = Modifier.height(12.dp))

                                Icon(
                                    imageVector = ProtonIcons.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = colors.brandNorm
                                )

                                Text(
                                    text = stringResource(R.string.title_2fa),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.textNorm,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 24.dp)
                                )

                                Text(
                                    text = stringResource(R.string.msg_2fa_instruction),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textWeak,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                SmoothOutlinedTextField(
                                    value = totpCode,
                                    onValueChange = { totpCode = it },
                                    label = { Text(stringResource(R.string.hint_2fa_code)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (totpCode.isNotBlank()) {
                                                viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, totpCode)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.brandNorm,
                                        unfocusedBorderColor = colors.shade20,
                                        focusedTextColor = colors.textNorm,
                                        unfocusedTextColor = colors.textNorm
                                    ),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, totpCode)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                                    enabled = totpCode.isNotBlank() && uiState !is LoginUiState.Loading
                                ) {
                                    if (uiState is LoginUiState.Loading) {
                                        ExpressiveCircularProgressIndicator(color = colors.textInverted, modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(stringResource(R.string.btn_verify), fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (uiState is LoginUiState.Error) {
                                    Text(
                                        text = (uiState as LoginUiState.Error).message,
                                        color = colors.notificationError,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = colors.backgroundNorm,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .statusBarsPadding()
                                    .widthIn(max = 480.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                NavigationHeader(title = "", onBack = onBackClick)

                                Spacer(modifier = Modifier.height(12.dp))

                                // Logo representation
                                Icon(
                                    imageVector = ProtonIcons.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = colors.brandNorm
                                )

                                Text(
                                    text = stringResource(R.string.login_title),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textNorm,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 24.dp, start = 24.dp, end = 24.dp)
                                )

                                Text(
                                    text = stringResource(R.string.login_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textWeak,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                    SmoothOutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text(stringResource(R.string.hint_username)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.brandNorm,
                                            unfocusedBorderColor = colors.shade20,
                                            focusedTextColor = colors.textNorm,
                                            unfocusedTextColor = colors.textNorm
                                        ),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    SmoothOutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text(stringResource(R.string.hint_password)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (username.isNotBlank() && password.isNotBlank()) {
                                                    checkVpnAndLogin()
                                                }
                                            }
                                        ),
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            val image = if (passwordVisible) ProtonIcons.Eye else ProtonIcons.EyeSlash
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(imageVector = image, contentDescription = stringResource(R.string.desc_toggle_password), tint = colors.iconWeak)
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.brandNorm,
                                            unfocusedBorderColor = colors.shade20,
                                            focusedTextColor = colors.textNorm,
                                            unfocusedTextColor = colors.textNorm
                                        ),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = { checkVpnAndLogin() },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm),
                                        enabled = uiState !is LoginUiState.Loading && username.isNotBlank() && password.isNotBlank()
                                    ) {
                                        if (uiState is LoginUiState.Loading) {
                                            ExpressiveCircularProgressIndicator(color = colors.textInverted, modifier = Modifier.size(24.dp))
                                        } else {
                                            Text(stringResource(R.string.btn_login), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = { /* TODO: Registration flow */ },
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.interactionNorm, contentColor = colors.textInverted),
                                        enabled = uiState !is LoginUiState.Loading
                                    ) {
                                        Text(text = stringResource(R.string.btn_create_account), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }

                                    if (uiState is LoginUiState.Error) {
                                        Text(
                                            text = (uiState as LoginUiState.Error).message,
                                            color = colors.notificationError,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Forgot password textual button mapped from ProtonTextButton
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        TextButton(
                                            onClick = { /* TODO: Forgot Password flow */ },
                                            modifier = Modifier.height(48.dp),
                                            enabled = uiState !is LoginUiState.Loading
                                        ) {
                                            Text(
                                                text = stringResource(R.string.forgot_password),
                                                color = colors.textAccent,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        TextButton(
                                            onClick = { showTokenLoginDialog = true },
                                            modifier = Modifier.height(48.dp),
                                            enabled = uiState !is LoginUiState.Loading
                                        ) {
                                            Text(
                                                text = stringResource(R.string.btn_login_tokens),
                                                color = colors.textWeak,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showTokenLoginDialog) {
                        AlertDialog(
                            onDismissRequest = { showTokenLoginDialog = false },
                            title = { Text(stringResource(R.string.title_login_tokens)) },
                            text = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.msg_login_tokens_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textWeak,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    SmoothOutlinedTextField(
                                        value = sessionJson,
                                        onValueChange = { sessionJson = it },
                                        label = { Text(stringResource(R.string.hint_session_json)) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = colors.brandNorm,
                                            unfocusedBorderColor = colors.shade20,
                                            focusedTextColor = colors.textNorm,
                                            unfocusedTextColor = colors.textNorm
                                        )
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (sessionJson.isNotBlank()) {
                                            viewModel.loginBySessionJson(sessionJson)
                                            showTokenLoginDialog = false
                                        }
                                    },
                                    enabled = sessionJson.isNotBlank()
                                ) {
                                    Text(stringResource(R.string.btn_login))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTokenLoginDialog = false }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            },
                            containerColor = colors.backgroundSecondary,
                            titleContentColor = colors.textNorm,
                            textContentColor = colors.textWeak
                        )
                    }
                }
            }
        }
    }
}
