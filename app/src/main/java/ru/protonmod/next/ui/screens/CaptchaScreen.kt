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
import android.util.AndroidRuntimeException
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import ru.protonmod.next.R
import ru.protonmod.next.data.local.SettingsManager
import ru.protonmod.next.ui.components.ExpressiveCircularProgressIndicator
import ru.protonmod.next.ui.components.ExpressiveLinearProgressIndicator
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.utils.DeviceInfoProvider
import ru.protonmod.next.utils.ProtonLogger
import ru.protonmod.next.ui.utils.isTablet
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptchaScreen(
    webUrl: String,
    onCaptchaSolve: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    isApiBypassEnabled: Boolean = false,
    apiBypassStrategy: String = "netlify",
    okHttpClient: OkHttpClient? = null
) {
    val colors = ProtonNextTheme.colors
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var hasSolved by remember { mutableStateOf(false) }
    val isTablet = isTablet()

    // Create a dedicated DoH client to fix ERR_NAME_NOT_RESOLVED without intrusive global interceptors.
    // The global OkHttpClient contains dynamicBaseUrlInterceptor and AuthLoggingInterceptor 
    // which inject headers and rewrite URLs in ways that may cause 404 on verify.proton.me.
    val dohClient = remember {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val doh = DnsOverHttps.Builder().client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .build()

        OkHttpClient.Builder()
            .dns(doh)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Determine which client to use:
    // - If bypass is enabled, we MUST use the global client to go through proxies.
    // - If bypass is disabled, we MUST use the dedicated DoH client to avoid 404s while still fixing resolution.
    val effectiveClient = remember(isApiBypassEnabled, okHttpClient, dohClient) {
        if (isApiBypassEnabled && okHttpClient != null) okHttpClient else dohClient
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.captcha_title),
                            color = colors.textNorm,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.desc_close),
                                tint = colors.textNorm
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.backgroundNorm
                    )
                )
                if (isLoading) {
                    ExpressiveLinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.brandNorm,
                        trackColor = colors.shade20
                    )
                }
            }
        },
        containerColor = colors.backgroundNorm
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.backgroundNorm),
            horizontalAlignment = if (isTablet) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Surface(
                modifier = if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth(),
                color = colors.backgroundNorm,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.captcha_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textWeak
                    )

                    if (isApiBypassEnabled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Text(
                                text = stringResource(R.string.captcha_proxy_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Box(
                modifier = (if (isTablet) Modifier.widthIn(max = 600.dp) else Modifier.fillMaxWidth())
                    .weight(1f)
            ) {
                var webView by remember { mutableStateOf<WebView?>(null) }
                var webViewUnavailable by remember { mutableStateOf(false) }

                val proxyBaseUrl = when (apiBypassStrategy) {
                    SettingsManager.STRATEGY_CLOUDFLARE -> "https://api.protonnext.qzz.io"
                    SettingsManager.STRATEGY_DENO -> "https://quick-bluejay-8760.smh01-mirrors.deno.net"
                    else -> "https://shimmering-stroopwafel-51675e.netlify.app"
                }

                LaunchedEffect(webUrl, sessionId, webView) {
                    if (webViewUnavailable) return@LaunchedEffect
                    val wv = webView ?: return@LaunchedEffect

                    // Normalize to direct URL so shouldInterceptRequest can match it
                    val directWebUrl = try {
                        val httpUrl = webUrl.toHttpUrl()
                        val host = httpUrl.host
                        if (host.endsWith("netlify.app") || host.endsWith("qzz.io")) {
                            val isApi = host.contains("-api") || httpUrl.encodedPath.contains("/verify-api")
                            val directHost = if (isApi) "verify-api.proton.me" else "verify.proton.me"
                            httpUrl.newBuilder().host(directHost).build().toString()
                        } else {
                            webUrl
                        }
                    } catch (_: Exception) {
                        webUrl
                    }

                    val optimizedUrl = buildString {
                        append(directWebUrl)
                        if (!directWebUrl.contains("?")) append("?") else append("&")
                        append("embed=true&theme=1&vpn=true")
                    }

                    val extraHeaders = mutableMapOf(
                        "x-pm-appversion" to "android-vpn@${DeviceInfoProvider.SPOOFED_APP_VERSION}-dev+play",
                        "x-pm-apiversion" to "4",
                        "Accept" to "application/vnd.protonmail.v1+json"
                    )
                    if (sessionId != null) {
                        extraHeaders["x-pm-uid"] = sessionId
                    }

                    ProtonLogger.d("CaptchaScreen", "Loading URL: $optimizedUrl")
                    wv.loadUrl(optimizedUrl, extraHeaders)
                }

                if (webViewUnavailable) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.captcha_webview_unavailable_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textNorm,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.captcha_webview_unavailable_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textWeak
                            )
                        }
                    }
                } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        try {
                        WebView(context).apply {
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                            val customUserAgent = DeviceInfoProvider.getSpoofedUserAgent()
                            settings.userAgentString = customUserAgent

                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            val jsInterface = object {
                                @JavascriptInterface
                                fun dispatch(response: String) {
                                    try {
                                        ProtonLogger.d("CaptchaScreen", "JS Dispatch: $response")
                                        val json = JSONObject(response)
                                        val type = json.optString("type")

                                        if ((type == "HUMAN_VERIFICATION_SUCCESS" || type == "Success") && !hasSolved) {
                                            val payload = json.optJSONObject("payload")
                                            val token = payload?.optString("token")

                                            if (!token.isNullOrEmpty()) {
                                                hasSolved = true
                                                coroutineScope.launch {
                                                    onCaptchaSolve(token)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        ProtonLogger.e("CaptchaScreen", "JS Parse Error", e)
                                    }
                                }
                            }

                            addJavascriptInterface(jsInterface, "AndroidInterface")

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    if (newProgress >= 90) {
                                        isLoading = false
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    ProtonLogger.e("CaptchaScreen", "WebView Error (${error?.errorCode}): ${error?.description}")
                                    isLoading = false
                                }

                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    onDismiss()
                                    return true
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val originalUrl = request.url.toString()
                                    
                                    // Intercept all GET requests to Proton domains to provide DoH/Proxy support
                                    // and fix DNS resolution issues (ERR_NAME_NOT_RESOLVED).
                                    val isProtonDomain = originalUrl.contains("proton.me") || 
                                                       originalUrl.contains("protonmail.ch") ||
                                                       originalUrl.contains("protonvpn.ch") ||
                                                       originalUrl.contains("protonvpn.com")

                                    if (request.method == "GET" && isProtonDomain) {
                                        try {
                                            val okRequest = Request.Builder()
                                                .url(originalUrl)
                                                .apply {
                                                    val headersMap = request.requestHeaders ?: emptyMap()
                                                    headersMap.forEach { (key, value) ->
                                                        if (!key.equals("Host", ignoreCase = true)) {
                                                            addHeader(key, value)
                                                        }
                                                    }

                                                    // Re-inject critical headers
                                                    if (sessionId != null && !headersMap.keys.any { it.equals("x-pm-uid", ignoreCase = true) }) {
                                                        addHeader("x-pm-uid", sessionId)
                                                    }
                                                    if (!headersMap.keys.any { it.equals("x-pm-appversion", ignoreCase = true) }) {
                                                        addHeader("x-pm-appversion", "android-vpn@${DeviceInfoProvider.SPOOFED_APP_VERSION}-dev+play")
                                                    }
                                                    if (!headersMap.keys.any { it.equals("x-pm-apiversion", ignoreCase = true) }) {
                                                        addHeader("x-pm-apiversion", "4")
                                                    }
                                                }
                                                .build()

                                            val response = effectiveClient.newCall(okRequest).execute()
                                            val contentTypeHeader = response.header("Content-Type", "application/octet-stream") ?: "application/octet-stream"
                                            val mimeType = contentTypeHeader.substringBefore(";").trim()
                                            val encoding = if (contentTypeHeader.contains("charset=")) {
                                                contentTypeHeader.substringAfter("charset=").substringBefore(";").trim()
                                            } else {
                                                "utf-8"
                                            }

                                            val responseHeaders = prepareProxyResponseHeaders(response)

                                            var bodyStream = response.body.byteStream()
                                            if (mimeType.contains("text/html", ignoreCase = true)) {
                                                val html = response.body.string()
                                                
                                                // If bypass is active, we still inject JS to help with POST requests 
                                                // by rewriting their URLs to resolvable proxy endpoints.
                                                val jsInject = if (isApiBypassEnabled && (
                                                    apiBypassStrategy == SettingsManager.STRATEGY_NETLIFY ||
                                                    apiBypassStrategy == SettingsManager.STRATEGY_CLOUDFLARE
                                                )) {
                                                    """
                                                    <script>
                                                    (function() {
                                                        var proxyBase = '$proxyBaseUrl';
                                                        function rewriteUrl(url) {
                                                            if (typeof url !== 'string') return url;
                                                            if (url.startsWith('https://verify-api.proton.me')) {
                                                                return url.replace('https://verify-api.proton.me', proxyBase + '/verify-api');
                                                            }
                                                            if (url.startsWith('https://verify.proton.me')) {
                                                                return url.replace('https://verify.proton.me', proxyBase + '/verify');
                                                            }
                                                            return url;
                                                        }
                                                        var origFetch = window.fetch;
                                                        window.fetch = function() {
                                                            if (arguments[0] instanceof Request) {
                                                                var newUrl = rewriteUrl(arguments[0].url);
                                                                if (newUrl !== arguments[0].url) {
                                                                    arguments[0] = new Request(newUrl, arguments[0]);
                                                                }
                                                            } else {
                                                                arguments[0] = rewriteUrl(arguments[0]);
                                                            }
                                                            return origFetch.apply(this, arguments);
                                                        };
                                                        var origOpen = XMLHttpRequest.prototype.open;
                                                        XMLHttpRequest.prototype.open = function() {
                                                            arguments[1] = rewriteUrl(arguments[1]);
                                                            return origOpen.apply(this, arguments);
                                                        };
                                                    })();
                                                    </script>
                                                    """.trimIndent()
                                                } else ""

                                                val injectedHtml = if (jsInject.isNotEmpty()) {
                                                    if (html.contains("<head>", ignoreCase = true)) {
                                                        html.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$jsInject")
                                                    } else {
                                                        jsInject + html
                                                    }
                                                } else html
                                                
                                                bodyStream = ByteArrayInputStream(injectedHtml.toByteArray())
                                            }

                                            return WebResourceResponse(mimeType, encoding, 200, "OK", responseHeaders, bodyStream)
                                        } catch (e: Exception) {
                                            ProtonLogger.e("CaptchaScreen", "Proxy/DoH Error for $originalUrl", e)
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }
                            webView = this
                        }
                        } catch (e: AndroidRuntimeException) {
                            ProtonLogger.e("CaptchaScreen", "WebView unavailable", e)
                            webViewUnavailable = true
                            View(context)
                        }
                    },
                    update = { /* No-op */ }
                )
                } // end else (webViewUnavailable)

                androidx.compose.animation.AnimatedVisibility(
                    visible = isLoading,
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.backgroundNorm),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveCircularProgressIndicator(color = colors.brandNorm)
                    }
                }
            }
        }
    }
}

private fun prepareProxyResponseHeaders(response: Response): MutableMap<String, String> {
    val headers = response.headers.toMap().toMutableMap()
    headers.keys.filter {
        it.equals("Content-Security-Policy", ignoreCase = true) ||
                it.equals("X-Frame-Options", ignoreCase = true)
    }.forEach { headers.remove(it) }
    headers["Access-Control-Allow-Origin"] = "*"
    return headers
}
