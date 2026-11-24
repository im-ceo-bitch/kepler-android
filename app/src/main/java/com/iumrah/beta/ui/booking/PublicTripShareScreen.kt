package com.iumrah.beta.ui.booking

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.settings.AppLanguage

/** Read-only public HTTPS page. No native credentials or JS interface are exposed. */
@SuppressLint("SetJavaScriptEnabled") // Trusted Next.js public page requires JavaScript.
@Composable
fun PublicTripShareScreen(token: String, language: AppLanguage, onBack: () -> Boolean) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        TextButton(onClick = { onBack() }) { Text(L10n.phrase("Back", language)) }
        if (!token.matches(Regex("^[A-Za-z0-9_-]{43}$"))) {
            Text(L10n.phrase("Invalid link", language))
            return@Column
        }
        key(token) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val uri = request.url
                                val parts = uri.pathSegments
                                val secure = uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443)
                                if (secure && uri.host == "iumrah.app" && parts.size == 2 &&
                                    parts[0] == "s" && parts[1].matches(Regex("^[A-Za-z0-9_-]{43}$"))) return false
                                // External actions only on a deliberate top-level tap, never from frames or redirects.
                                if (secure && uri.host != null && request.isForMainFrame && request.hasGesture()) {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                                }
                                return true
                            }
                        }
                        loadUrl("https://iumrah.app/s/$token")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> view.stopLoading(); view.destroy() },
            )
        }
    }
}
