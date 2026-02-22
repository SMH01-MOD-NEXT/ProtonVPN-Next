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
import androidx.compose.ui.Alignment
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

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        when (uiState) {
            is LoginUiState.Success -> {
                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                onLoginSuccess() // triggers navigation to dashboard
            }
            is LoginUiState.Error -> {
                val errorMessage = (uiState as LoginUiState.Error).message
                Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_LONG).show()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
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
                            Text("Security Check", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                            Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Enter the 6-digit TOTP code from your authenticator app.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = totpCode,
                                onValueChange = { totpCode = it },
                                label = { Text("6-digit Code") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, totpCode) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large,
                                enabled = totpCode.isNotBlank() && !isLoading
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                else Text("Verify", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    // --- Standard Login View ---
                    else -> {
                        Column {
                            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.hint_username)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                enabled = !isLoading
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
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) { Icon(imageVector = image, contentDescription = "Toggle") }
                                }
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.login(username, password) },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = MaterialTheme.shapes.large,
                                enabled = username.isNotBlank() && password.isNotBlank() && !isLoading
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                else Text(stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}