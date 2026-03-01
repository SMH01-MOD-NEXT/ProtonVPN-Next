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

package ru.protonmod.next.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import ru.protonmod.next.R
import ru.protonmod.next.ui.theme.ProtonNextTheme

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val colors = ProtonNextTheme.colors
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> {
                Toast.makeText(context, context.getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                onLoginSuccess() // triggers navigation to dashboard
            }
            is LoginUiState.Error -> {
                val errorMessage = (uiState as LoginUiState.Error).message
                Toast.makeText(context, context.getString(R.string.login_error, errorMessage), Toast.LENGTH_LONG).show()
                viewModel.resetError()
            }
            else -> {}
        }
    }

    val isLoading = uiState is LoginUiState.Loading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.desc_back_button),
                            tint = colors.textNorm
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.backgroundNorm)
            )
        },
        containerColor = colors.backgroundNorm
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = uiState, label = "login_state") { state ->
                when (state) {
                    is LoginUiState.RequiresCaptcha -> {
                        // --- CAPTCHA WebView ---
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                stringResource(R.string.title_security_check), 
                                style = MaterialTheme.typography.headlineMedium, 
                                fontWeight = FontWeight.Bold,
                                color = colors.textNorm
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true

                                        addJavascriptInterface(object : Any() {
                                            @JavascriptInterface
                                            fun onCaptchaSuccess(token: String) {
                                                Log.d("Captcha", "Got Token from JS: $token")
                                                viewModel.login(state.username, state.passwordRaw, token)
                                            }
                                        }, "ProtonCaptchaNative")

                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                val url = request?.url?.toString() ?: ""
                                                val token = request?.url?.getQueryParameter("token")
                                                if (!token.isNullOrBlank() && !url.contains("verify.proton.me")) {
                                                    viewModel.login(state.username, state.passwordRaw, token)
                                                    return true
                                                }
                                                return false
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                view?.evaluateJavascript(
                                                    """
                                                    window.addEventListener('message', function(event) {
                                                        if (event.data) {
                                                            var token = event.data.token || (typeof event.data === 'string' ? event.data : null);
                                                            if (token && token.length > 20) {
                                                                ProtonCaptchaNative.onCaptchaSuccess(token);
                                                            }
                                                        }
                                                    });
                                                    """.trimIndent(), null
                                                )
                                            }
                                        }
                                        loadUrl(state.webUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    is LoginUiState.Requires2FA -> {
                        // --- 2FA Input View ---
                        Column {
                            Text(stringResource(R.string.title_2fa), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = colors.textNorm)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.msg_2fa_instruction), style = MaterialTheme.typography.bodyLarge, color = colors.textWeak)
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = totpCode,
                                onValueChange = { totpCode = it },
                                label = { Text(stringResource(R.string.hint_2fa_code)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isLoading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.brandNorm,
                                    unfocusedBorderColor = colors.separatorNorm,
                                    focusedLabelColor = colors.brandNorm,
                                    unfocusedLabelColor = colors.textWeak,
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                )
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, totpCode) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large,
                                enabled = totpCode.isNotBlank() && !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.interactionNorm,
                                    contentColor = colors.textInverted
                                )
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.textInverted, strokeWidth = 2.dp)
                                else Text(stringResource(R.string.btn_verify), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // --- Standard Login View ---
                    else -> {
                        Column {
                            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = colors.textNorm)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge, color = colors.textWeak)
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.hint_username)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                enabled = !isLoading,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.brandNorm,
                                    unfocusedBorderColor = colors.separatorNorm,
                                    focusedLabelColor = colors.brandNorm,
                                    unfocusedLabelColor = colors.textWeak,
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(stringResource(R.string.hint_password)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                enabled = !isLoading,
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    val image = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) { 
                                        Icon(
                                            imageVector = image, 
                                            contentDescription = stringResource(R.string.desc_toggle_password),
                                            tint = colors.iconWeak
                                        ) 
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.brandNorm,
                                    unfocusedBorderColor = colors.separatorNorm,
                                    focusedLabelColor = colors.brandNorm,
                                    unfocusedLabelColor = colors.textWeak,
                                    focusedTextColor = colors.textNorm,
                                    unfocusedTextColor = colors.textNorm
                                )
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.login(username, password) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large,
                                enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.interactionNorm,
                                    contentColor = colors.textInverted
                                )
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = colors.textInverted, strokeWidth = 2.dp)
                                else Text(stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
