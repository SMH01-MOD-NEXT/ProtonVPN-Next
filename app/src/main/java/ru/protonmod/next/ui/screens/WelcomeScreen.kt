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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
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
import ru.protonmod.next.BuildConfig
import ru.protonmod.next.R
import ru.protonmod.next.data.local.ServerLoadDisplayMode
import ru.protonmod.next.data.local.SetupStep
import ru.protonmod.next.ui.components.*
import ru.protonmod.next.ui.icons.ProtonIcons
import ru.protonmod.next.ui.screens.settings.LoadModePreviewCard
import ru.protonmod.next.ui.screens.settings.ThemePreviewCard
import ru.protonmod.next.ui.theme.AppTheme
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.ui.theme.liquidGlass
import ru.protonmod.next.utils.ProtonLogger

@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToApiBypass: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val persistedStep by viewModel.setupStep.collectAsStateWithLifecycle()
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    var isInitialized by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isByeDpiAutoTesting by viewModel.isByeDpiAutoTesting.collectAsStateWithLifecycle()
    val byeDpiProgress by viewModel.byeDpiStrategyTester.progress.collectAsStateWithLifecycle()
    val byeDpiCurrentStrategy by viewModel.byeDpiStrategyTester.currentStrategy.collectAsStateWithLifecycle()
    val colors = ProtonNextTheme.colors

    val savedUsername by viewModel.username.collectAsStateWithLifecycle()
    val isApiBypassEnabled by viewModel.isApiBypassEnabled.collectAsStateWithLifecycle()
    val apiBypassStrategy by viewModel.apiBypassStrategy.collectAsStateWithLifecycle()
    var isSkipping by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(colors.backgroundNorm)) {
        val advanceTo: (SetupStep) -> Unit = { nextStep ->
            currentStep = nextStep
            if (nextStep.ordinal > persistedStep.ordinal) {
                viewModel.setSetupStep(nextStep)
            }
        }

        LaunchedEffect(persistedStep) {
            if (!isInitialized) {
                if (persistedStep != SetupStep.WELCOME && persistedStep != SetupStep.COMPLETE) {
                    currentStep = persistedStep
                }
                isInitialized = true
            }
        }
        LaunchedEffect(currentStep) {
            ProtonLogger.d("WelcomeScreen", "Current Step changed to: $currentStep")
        }

        LaunchedEffect(isByeDpiAutoTesting) {
            if (isByeDpiAutoTesting) {
                currentStep = SetupStep.LOADING
            }
        }

        LaunchedEffect(uiState, isSkipping, onNavigateToHome) {
            ProtonLogger.d("WelcomeScreen", "UiState changed to: $uiState")
            when (uiState) {
                is LoginUiState.Loading -> {
                    ProtonLogger.i("WelcomeScreen", "Transitioning to LOADING step")
                    currentStep = SetupStep.LOADING
                }
                is LoginUiState.Success -> {
                    if (isSkipping || persistedStep == SetupStep.COMPLETE) {
                        viewModel.setSetupStep(SetupStep.COMPLETE)
                        onNavigateToHome()
                    } else {
                        advanceTo(SetupStep.CONFIG_PORT)
                    }
                }
                is LoginUiState.Requires2FA -> {
                    currentStep = SetupStep.LOGIN_2FA
                }
                is LoginUiState.RequiresCaptcha -> {
                    currentStep = SetupStep.CAPTCHA
                }
                is LoginUiState.Error -> {
                    // If we were loading, go back to appropriate login step on error
                    if (currentStep == SetupStep.LOADING) {
                        currentStep = if (savedUsername.isBlank()) SetupStep.WELCOME else SetupStep.LOGIN_PASSWORD
                    }
                }
                else -> {}
            }
        }

        BackHandler(currentStep != SetupStep.WELCOME && currentStep != SetupStep.LOADING) {
            currentStep = when (currentStep) {
                SetupStep.LOGIN_EMAIL -> SetupStep.WELCOME
                SetupStep.LOGIN_PASSWORD -> SetupStep.LOGIN_EMAIL
                SetupStep.LOGIN_2FA -> SetupStep.LOGIN_PASSWORD
                SetupStep.CAPTCHA -> {
                    val state = uiState as? LoginUiState.RequiresCaptcha
                    if (state?.isAnonymous == true) SetupStep.WELCOME else SetupStep.LOGIN_PASSWORD
                }
                SetupStep.CONFIG_PORT -> SetupStep.WELCOME
                SetupStep.CONFIG_OBFUSCATION -> SetupStep.CONFIG_PORT
                SetupStep.CONFIG_SERVER_LOAD -> SetupStep.CONFIG_OBFUSCATION
                SetupStep.CONFIG_THEME -> SetupStep.CONFIG_SERVER_LOAD
                SetupStep.CONFIG_TELEMETRY -> SetupStep.CONFIG_THEME
                SetupStep.COMPLETE -> if (BuildConfig.IS_PRIVACY_BUILD) {
                    SetupStep.CONFIG_THEME
                } else {
                    SetupStep.CONFIG_TELEMETRY
                }
                else -> SetupStep.WELCOME
            }
            viewModel.resetError()
        }

        // Top Atmospheric Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.brandNorm.copy(alpha = 0.4f),
                        colors.brandNorm.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(size.width / 2, -size.height * 0.1f),
                    radius = size.width * 1.5f
                ),
                center = androidx.compose.ui.geometry.Offset(size.width / 2, -size.height * 0.1f),
                radius = size.width * 1.5f
            )
        }

        ExpressiveBackground(step = currentStep)

        if (currentStep == SetupStep.WELCOME || 
            currentStep == SetupStep.LOGIN_EMAIL || 
            currentStep == SetupStep.LOGIN_PASSWORD || 
            currentStep == SetupStep.LOGIN_2FA) {
            Surface(
                onClick = onNavigateToApiBypass,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                shape = CircleShape,
                color = colors.brandNorm.copy(alpha = 0.15f),
                contentColor = colors.brandNorm,
                border = BorderStroke(1.dp, colors.brandNorm.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = ProtonIcons.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_api_bypass),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (currentStep.ordinal >= SetupStep.CONFIG_PORT.ordinal && currentStep != SetupStep.COMPLETE) {
            TextButton(
                onClick = {
                    viewModel.setSetupStep(SetupStep.COMPLETE)
                    onNavigateToHome()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_skip),
                    color = colors.brandNorm,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "setup_wizard",
            modifier = Modifier.fillMaxSize()
        ) { step ->
            when (step) {
                SetupStep.WELCOME -> StepWelcome(
                    onLogin = { currentStep = SetupStep.LOGIN_EMAIL },
                    onGuest = { viewModel.loginAnonymous() },
                    onPrivacyPolicy = onNavigateToPrivacyPolicy
                )
                SetupStep.LOGIN_EMAIL -> StepLoginEmail(
                    initialEmail = savedUsername,
                    onNext = { email ->
                        viewModel.setUsername(email)
                        currentStep = SetupStep.LOGIN_PASSWORD
                    },
                    onBack = { currentStep = SetupStep.WELCOME }
                )
                SetupStep.LOGIN_PASSWORD -> StepLoginPassword(
                    email = savedUsername,
                    uiState = uiState,
                    onLogin = { pass -> viewModel.login(savedUsername, pass) },
                    onBack = { currentStep = SetupStep.LOGIN_EMAIL }
                )
                SetupStep.LOGIN_2FA -> StepLogin2FA(
                    uiState = uiState,
                    onVerify = { code ->
                        val state = uiState as? LoginUiState.Requires2FA
                        if (state != null) {
                            viewModel.submit2FA(state.sessionId, state.tempAccessToken, state.refreshToken, code)
                        }
                    },
                    onBack = { currentStep = SetupStep.LOGIN_PASSWORD }
                )
                SetupStep.CAPTCHA -> {
                    val state = uiState as? LoginUiState.RequiresCaptcha
                    if (state != null) {
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
                SetupStep.LOADING -> SetupLoadingScreen(
                    message = if (isByeDpiAutoTesting) stringResource(R.string.byedpi_auto_test_title)
                              else stringResource(R.string.setup_please_wait),
                    step = step,
                    progress = if (isByeDpiAutoTesting) byeDpiProgress else null,
                    currentStrategy = if (isByeDpiAutoTesting) byeDpiCurrentStrategy else null,
                    onSkip = if (isByeDpiAutoTesting) { { viewModel.stopAutoByeDpiTest() } } else null
                )
                SetupStep.CONFIG_PORT -> StepConfigPort(
                    onNext = { port ->
                        viewModel.setVpnPort(port)
                        advanceTo(SetupStep.CONFIG_OBFUSCATION)
                    },
                    onBack = { currentStep = SetupStep.WELCOME }
                )
                SetupStep.CONFIG_OBFUSCATION -> StepConfigObfuscation(
                    onNext = { enabled ->
                        viewModel.setObfuscationEnabled(enabled)
                        advanceTo(SetupStep.CONFIG_SERVER_LOAD)
                    },
                    onBack = { currentStep = SetupStep.CONFIG_PORT }
                )
                SetupStep.CONFIG_SERVER_LOAD -> StepConfigServerLoad(
                    onNext = { mode ->
                        viewModel.setServerLoadDisplayMode(mode)
                        advanceTo(SetupStep.CONFIG_THEME)
                    },
                    onBack = { currentStep = SetupStep.CONFIG_OBFUSCATION }
                )
                SetupStep.CONFIG_THEME -> StepConfigTheme(
                    onNext = { theme ->
                        viewModel.setAppTheme(theme)
                        advanceTo(
                            if (BuildConfig.IS_PRIVACY_BUILD) SetupStep.COMPLETE
                            else SetupStep.CONFIG_TELEMETRY
                        )
                    },
                    onBack = { currentStep = SetupStep.CONFIG_SERVER_LOAD }
                )
                SetupStep.CONFIG_TELEMETRY -> {
                    if (BuildConfig.IS_PRIVACY_BUILD) {
                        LaunchedEffect(viewModel) {
                            viewModel.setSetupStep(SetupStep.COMPLETE)
                            currentStep = SetupStep.COMPLETE
                        }
                    } else {
                        StepConfigTelemetry(
                            onNext = { telemetry ->
                                viewModel.setTelemetrySettings(telemetry)
                                currentStep = SetupStep.COMPLETE
                            },
                            onBack = { currentStep = SetupStep.CONFIG_THEME }
                        )
                    }
                }
                SetupStep.COMPLETE -> StepComplete(
                    onFinish = onNavigateToHome
                )
            }
        }
    }
}

@Composable
private fun StepWelcome(
    onLogin: () -> Unit,
    onGuest: () -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    val colors = ProtonNextTheme.colors
    val privacyPolicyText = stringResource(R.string.settings_privacy_policy)
    val disclaimerText = stringResource(R.string.settings_disclaimer)
    val agreementTemplate = stringResource(R.string.welcome_agreement_text, privacyPolicyText, disclaimerText)

    val annotatedString = buildAnnotatedString {
        val privacyIndex = agreementTemplate.indexOf(privacyPolicyText)
        val disclaimerIndex = agreementTemplate.indexOf(disclaimerText)

        append(agreementTemplate)

        val linkStyle = SpanStyle(color = colors.brandNorm, fontWeight = FontWeight.Bold)

        if (privacyIndex != -1) {
            addLink(
                clickable = LinkAnnotation.Clickable(
                    tag = "privacy",
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { onPrivacyPolicy() }
                ),
                start = privacyIndex,
                end = privacyIndex + privacyPolicyText.length
            )
        }

        if (disclaimerIndex != -1) {
            addLink(
                clickable = LinkAnnotation.Clickable(
                    tag = "disclaimer",
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = { onPrivacyPolicy() }
                ),
                start = disclaimerIndex,
                end = disclaimerIndex + disclaimerText.length
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.brandNorm.copy(alpha = 0.25f),
                                colors.brandNorm.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // vpn_welcome_globe is a raster (.webp) asset. painterResource() picks the
            // loader from the cached resource entry and can route it into the vector
            // loader after a configuration change, which crashed onboarding with
            // "Only VectorDrawables and rasterized asset types are supported" (ANDROID-232).
            // imageResource() always uses the raster decoder, so the type can never mismatch.
            val welcomeGlobe: ImageBitmap = ImageBitmap.imageResource(id = R.drawable.vpn_welcome_globe)
            Image(
                bitmap = welcomeGlobe,
                contentDescription = null,
                modifier = Modifier.size(180.dp)
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        
        val baseStyle = MaterialTheme.typography.headlineLarge
        val minFontSize = MaterialTheme.typography.titleMedium.fontSize
        var titleFontSize by remember { mutableStateOf(baseStyle.fontSize) }
        var readyToDraw by remember { mutableStateOf(false) }

        Text(
            text = stringResource(R.string.welcome_title),
            style = baseStyle.copy(fontSize = titleFontSize),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textNorm,
            maxLines = 2,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow && titleFontSize > minFontSize) {
                    titleFontSize *= 0.9f
                } else {
                    readyToDraw = true
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp).graphicsLayer { alpha = if (readyToDraw) 1f else 0f }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colors.textWeak,
            modifier = Modifier.graphicsLayer { alpha = if (readyToDraw) 1f else 0f }
        )
        Spacer(modifier = Modifier.weight(0.5f))

        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall.copy(
                textAlign = TextAlign.Center,
                color = colors.textWeak
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.btn_login), style = MaterialTheme.typography.labelLarge)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape
        ) {
            Text(stringResource(R.string.btn_continue_guest), color = colors.textNorm)
        }
    }
}

@Composable
private fun StepLoginEmail(
    initialEmail: String,
    onNext: (String) -> Unit,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.User, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        SmoothOutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.hint_username)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            leadingIcon = { Icon(ProtonIcons.Envelope, null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (email.isNotBlank()) onNext(email) })
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(email) },
            nextText = stringResource(R.string.troubleshoot_btn_next),
            nextEnabled = email.isNotBlank()
        )
    }
}

@Composable
private fun StepLoginPassword(
    email: String,
    uiState: LoginUiState,
    onLogin: (String) -> Unit,
    onBack: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(ProtonIcons.User, null, tint = colors.brandNorm)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = email, style = MaterialTheme.typography.titleMedium, color = colors.textNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SmoothOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.hint_password)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            leadingIcon = { Icon(ProtonIcons.Lock, null) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) ProtonIcons.Eye else ProtonIcons.EyeSlash, null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (password.isNotBlank()) onLogin(password) })
        )
        
        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = colors.notificationError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onLogin(password) },
            nextText = stringResource(R.string.btn_login),
            nextEnabled = password.isNotBlank()
        )
    }
}

@Composable
private fun StepLogin2FA(
    uiState: LoginUiState,
    onVerify: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        Text(
            text = stringResource(R.string.title_2fa),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.msg_2fa_instruction),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SmoothOutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            label = { Text(stringResource(R.string.hint_2fa_code)) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onVerify(code) })
        )
        
        if (uiState is LoginUiState.Error) {
            Text(
                text = uiState.message,
                color = colors.notificationError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onVerify(code) },
            nextText = stringResource(R.string.btn_verify),
            nextEnabled = code.length == 6
        )
    }
}


@Composable
private fun StepConfigPort(onNext: (Int) -> Unit, onBack: () -> Unit) {
    var selectedPort by remember { mutableIntStateOf(0) }
    val colors = ProtonNextTheme.colors
    val portOptions = remember { listOf(0, 443, 123, 1194, 51820) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.Servers, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.settings_port),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.settings_port_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(portOptions) { port ->
                val isSelected = port == selectedPort
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedPort = port }
                        .liquidGlass(
                            shape = RoundedCornerShape(20.dp),
                            alpha = if (isSelected) 0.3f else 0.1f,
                            shadowElevation = 0.dp
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (port == 0) stringResource(R.string.settings_port_auto) else port.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.brandNorm else colors.textNorm
                            )
                        }
                        if (isSelected) {
                            Icon(ProtonIcons.CheckmarkCircle, null, tint = colors.brandNorm)
                        } else {
                            RadioButton(selected = false, onClick = null)
                        }
                    }
                }
            }
        }
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedPort) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigObfuscation(onNext: (Boolean) -> Unit, onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.Shield, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.obfuscation_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.obfuscation_info_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .liquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    alpha = if (enabled) 0.3f else 0.1f,
                    shadowElevation = 0.dp
                )
                .clickable { enabled = !enabled }
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.obfuscation_enable),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textNorm
                    )
                    Text(
                        text = stringResource(R.string.obfuscation_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textWeak
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.brandNorm,
                        checkedThumbColor = colors.onInteraction
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(enabled) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigServerLoad(onNext: (ServerLoadDisplayMode) -> Unit, onBack: () -> Unit) {
    var selectedMode by remember { mutableStateOf(ServerLoadDisplayMode.ALL) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.ChartLine, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = stringResource(R.string.settings_load_display_mode),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(ServerLoadDisplayMode.entries) { mode ->
                LoadModePreviewCard(
                    mode = mode,
                    isSelected = selectedMode == mode,
                    onClick = { selectedMode = mode }
                )
            }
        }
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedMode) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigTheme(onNext: (AppTheme) -> Unit, onBack: () -> Unit) {
    var selectedTheme by remember { mutableStateOf(AppTheme.DARK) }
    val colors = ProtonNextTheme.colors

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.Palette, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.settings_app_theme),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AppTheme.entries) { theme ->
                ThemePreviewCard(
                    theme = theme,
                    isSelected = selectedTheme == theme,
                    onClick = { selectedTheme = theme }
                )
            }
        }
        
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(selectedTheme) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun StepConfigTelemetry(
    onNext: (InitialTelemetrySettings) -> Unit,
    onBack: () -> Unit
) {
    var settings by remember { mutableStateOf(InitialTelemetrySettings()) }
    val colors = ProtonNextTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).background(Color.Transparent)
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.brandNorm.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(ProtonIcons.ShieldHalfFilled, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.setup_telemetry_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textNorm
        )
        Text(
            text = stringResource(R.string.setup_telemetry_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textWeak
        )
        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(contentType = "Note") {
                Text(
                    text = stringResource(R.string.setup_telemetry_default_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textWeak,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(RoundedCornerShape(16.dp), alpha = 0.2f, shadowElevation = 0.dp)
                        .padding(16.dp)
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_crash_reports),
                    subtitle = stringResource(R.string.settings_crash_reports_desc),
                    checked = settings.crashReports,
                    onCheckedChange = { settings = settings.copy(crashReports = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_non_fatal),
                    subtitle = stringResource(R.string.settings_sentry_non_fatal_desc),
                    checked = settings.nonFatalErrors,
                    onCheckedChange = { settings = settings.copy(nonFatalErrors = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_anr),
                    subtitle = stringResource(R.string.settings_sentry_anr_desc),
                    checked = settings.anrDetection,
                    onCheckedChange = { settings = settings.copy(anrDetection = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_metrics),
                    subtitle = stringResource(R.string.settings_sentry_metrics_desc),
                    checked = settings.metrics,
                    onCheckedChange = { settings = settings.copy(metrics = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_logs),
                    subtitle = stringResource(R.string.settings_sentry_logs_desc),
                    checked = settings.logs,
                    onCheckedChange = { settings = settings.copy(logs = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_performance),
                    subtitle = stringResource(R.string.settings_sentry_performance_desc),
                    checked = settings.performance,
                    onCheckedChange = { settings = settings.copy(performance = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_analytics),
                    subtitle = stringResource(R.string.settings_analytics_desc),
                    checked = settings.analytics,
                    onCheckedChange = { settings = settings.copy(analytics = it) }
                )
            }
            item(contentType = "Toggle") {
                TelemetrySetupToggle(
                    title = stringResource(R.string.settings_sentry_session_replay),
                    subtitle = stringResource(R.string.settings_sentry_session_replay_desc),
                    checked = settings.sessionReplay,
                    onCheckedChange = { settings = settings.copy(sessionReplay = it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        WizardNavigation(
            onBack = onBack,
            onNext = { onNext(settings) },
            nextText = stringResource(R.string.troubleshoot_btn_next)
        )
    }
}

@Composable
private fun TelemetrySetupToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .liquidGlass(RoundedCornerShape(18.dp), alpha = 0.18f, shadowElevation = 0.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textNorm)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepComplete(onFinish: () -> Unit) {
    val colors = ProtonNextTheme.colors
    val baseStyle = MaterialTheme.typography.headlineMedium
    val minFontSize = MaterialTheme.typography.titleMedium.fontSize
    var titleFontSize by remember { mutableStateOf(baseStyle.fontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp).verticalScroll(rememberScrollState()).background(Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        Icon(ProtonIcons.CheckmarkCircle, null, modifier = Modifier.size(80.dp), tint = colors.brandNorm)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.setup_complete_title),
            style = baseStyle.copy(fontSize = titleFontSize),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colors.textNorm,
            maxLines = 1,
            onTextLayout = { textLayoutResult ->
                if (textLayoutResult.hasVisualOverflow && titleFontSize > minFontSize) {
                    titleFontSize *= 0.9f
                } else {
                    readyToDraw = true
                }
            },
            modifier = Modifier.padding(horizontal = 8.dp).graphicsLayer { alpha = if (readyToDraw) 1f else 0f }
        )
        Text(
            text = stringResource(R.string.setup_complete_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textWeak,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        ShowcaseCard(stringResource(R.string.setup_showcase_speed_title), stringResource(R.string.setup_showcase_speed_desc), ProtonIcons.Bolt)
        ShowcaseCard(stringResource(R.string.setup_showcase_privacy_title), stringResource(R.string.setup_showcase_privacy_desc), ProtonIcons.Fingerprint)
        ShowcaseCard(stringResource(R.string.setup_showcase_security_title), stringResource(R.string.setup_showcase_security_desc), ProtonIcons.Shield)
        ShowcaseCard(stringResource(R.string.setup_showcase_bypass_title), stringResource(R.string.setup_showcase_bypass_desc), ProtonIcons.Globe)
        
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.brandNorm)
        ) {
            Text(stringResource(R.string.setup_btn_finish))
        }
    }
}

@Composable
private fun WizardNavigation(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextText: String,
    nextEnabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.desc_back), color = ProtonNextTheme.colors.textWeak)
        }
        
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            shape = CircleShape,
            modifier = Modifier.height(56.dp).widthIn(min = 120.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ProtonNextTheme.colors.brandNorm)
        ) {
            Text(nextText)
        }
    }
}

@Composable
private fun ShowcaseCard(
    title: String,
    desc: String,
    icon: ImageVector
) {
    val colors = ProtonNextTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .liquidGlass(
                shape = RoundedCornerShape(24.dp),
                alpha = 0.05f,
                shadowElevation = 0.dp
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(32.dp), tint = colors.brandNorm)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textNorm)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = colors.textWeak)
        }
    }
}

