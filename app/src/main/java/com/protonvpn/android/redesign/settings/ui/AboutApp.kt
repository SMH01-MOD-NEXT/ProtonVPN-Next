package com.protonvpn.android.redesign.settings.ui

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.theme.ThemeType
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun AboutAppScreen(
    onClose: () -> Unit,
    themeType: ThemeType,
    appVersion: String
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val githubUrl = "https://github.com/SMH01-MOD-NEXT/ProtonMOD-NEXT"
    val telegramUrl = "https://t.me/ProtonVPN_MOD"

    val isAmoled = themeType == ThemeType.Amoled || themeType == ThemeType.NewYearAmoled
    val border = if (isAmoled) BorderStroke(1.dp, Color.White) else null

    val isLight = themeType == ThemeType.Light || themeType == ThemeType.NewYearLight ||
            (themeType == ThemeType.System && !isSystemInDarkTheme())

    val cardColor = if (isLight) Color(0xFFF0F0F0) else ProtonTheme.colors.backgroundSecondary

    FeatureSubSettingScaffold(
        title = stringResource(id = R.string.about_app_title),
        onClose = onClose,
        listState = listState,
        titleInListIndex = 0
    ) { contentPadding ->

        val horizontalPadding = 16.dp

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = largeScreenContentPadding())
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                setImageResource(R.mipmap.ic_launcher)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                        },
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // App name
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = ProtonTheme.typography.defaultNorm.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = androidx.compose.ui.unit.TextUnit.Unspecified
                        ),
                        color = ProtonTheme.colors.textNorm
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Version
                    Text(
                        text = "v$appVersion",
                        style = ProtonTheme.typography.defaultWeak,
                        color = ProtonTheme.colors.textWeak
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }

            item {
                Text(
                    text = stringResource(id = R.string.about_community),
                    style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Bold),
                    color = ProtonTheme.colors.textNorm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, start = 4.dp)
                )

                // 2. Links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // GitHub
                    AboutLinkCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(id = R.string.about_github),
                        iconRes = R.drawable.ic_github,
                        cardColor = cardColor,
                        border = border,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                            context.startActivity(intent)
                        }
                    )

                    // Telegram
                    AboutLinkCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(id = R.string.about_telegram),
                        iconRes = R.drawable.ic_telegram,
                        cardColor = cardColor,
                        border = border,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramUrl))
                            context.startActivity(intent)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AboutLinkCard(
    modifier: Modifier = Modifier,
    title: String,
    @DrawableRes iconRes: Int,
    cardColor: Color,
    border: BorderStroke?,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ProtonTheme.colors.interactionNorm.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = ProtonTheme.colors.interactionNorm,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = ProtonTheme.typography.defaultNorm.copy(fontWeight = FontWeight.Medium),
                color = ProtonTheme.colors.textNorm
            )
        }
    }
}