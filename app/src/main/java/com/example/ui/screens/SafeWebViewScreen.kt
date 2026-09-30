package com.nirogbhumi.app.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nirogbhumi.app.web.UrlPolicy

/**
 * A web page inside the app, locked down:
 * - only our own hosts load in the app (parsed host comparison, https only, see [UrlPolicy]); ordinary
 *   https links to other sites open in the user's browser, everything else is refused;
 * - no file/content access, no downloads, no pop-up windows, no mixed content, no JavaScript bridge to the
 *   app, no camera/microphone/location, and certificate errors are never bypassed;
 * - third-party cookies are off and the session's cache and history are cleared when the screen closes.
 */
@Composable
fun SafeWebViewScreen(title: String, startUrl: String, onBack: () -> Unit, allowedHosts: Set<String> = UrlPolicy.OWN_HOSTS) {
    val context = LocalContext.current
    var progress by remember { mutableStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val startAllowed = remember(startUrl) { UrlPolicy.isAllowedHttps(startUrl, allowedHosts) }

    BackHandler(enabled = true) { if (canGoBack) webView?.goBack() else onBack() }
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { view ->
                runCatching { view.stopLoading(); view.clearHistory(); view.clearCache(true); view.removeAllViews(); view.destroy() }
            }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF))) {
        DetailScreenHeader(title, onBack = onBack, trailing = {
            TextButton(onClick = { failed = false; webView?.reload() }) { Text("Reload", color = Color(0xFF314936)) }
        })
        if (!startAllowed) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("This page can't be opened here.", fontSize = 14.sp, color = Color(0xFF697169))
            }
            return@Column
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF314936))
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        hardenWebView(this)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url.toString()
                                return when (UrlPolicy.navigation(target, allowedHosts)) {
                                    UrlPolicy.Navigation.LOAD_IN_APP -> false
                                    UrlPolicy.Navigation.OPEN_IN_BROWSER -> { openInBrowser(context, target); true }
                                    UrlPolicy.Navigation.BLOCK -> true
                                }
                            }
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { failed = false; canGoBack = view.canGoBack() }
                            override fun onPageFinished(view: WebView, url: String?) { canGoBack = view.canGoBack() }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) failed = true }
                            // Never proceed past a certificate problem.
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); failed = true }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean { failed = true; return true }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
                            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false
                        }
                        setDownloadListener { _, _, _, _, _ -> /* downloads are not allowed in this view */ }
                        webView = this
                        loadUrl(startUrl)
                    }
                },
            )
            if (failed) {
                Column(Modifier.fillMaxSize().background(Color(0xFFF8F6EF)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                    Text("We couldn't load this page. Check your connection and try again.", fontSize = 14.sp, color = Color(0xFF697169))
                    TextButton(onClick = { failed = false; webView?.reload() }) { Text("Try again", color = Color(0xFF314936)) }
                }
            } else if (progress == 0) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF314936))
            }
        }
    }
}

/** The one place the WebView's security settings are decided. */
@SuppressLint("SetJavaScriptEnabled")
fun hardenWebView(view: WebView) {
    view.settings.apply {
        javaScriptEnabled = true            // the store needs it; only our own hosts load in this view
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        safeBrowsingEnabled = true
        saveFormData = false
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
    // No addJavascriptInterface anywhere: a page must have no way to call into the app.
}

private fun openInBrowser(context: android.content.Context, url: String) {
    if (!UrlPolicy.isSafeToOpenExternally(url)) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
