package com.xiaoiubao.suixinji

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayInputStream

internal object SchoolWebPolicy {
    fun allows(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    // Only table text is copied. No form values, scripts, cookies, tokens, or native bridge.
    val captureScript = """
        (function () {
          var html = '', unreadable = false, seen = [];
          function cleanTable(table) {
            var copy = table.cloneNode(true);
            copy.querySelectorAll('script,style,input,textarea,select,button,iframe,object,embed,svg,img').forEach(function (e) { e.remove(); });
            [copy].concat(Array.from(copy.querySelectorAll('*'))).forEach(function (e) {
              Array.from(e.attributes).forEach(function (a) {
                if (['rowspan','colspan','class','data-course'].indexOf(a.name) < 0) e.removeAttribute(a.name);
                else if (a.name === 'data-course') e.setAttribute(a.name, '');
              });
            });
            return copy.outerHTML;
          }
          function scan(win, depth) {
            if (depth > 4 || seen.indexOf(win) >= 0) return;
            seen.push(win);
            try {
              var doc = win.document;
              doc.querySelectorAll('table').forEach(function (table) {
                if (!table.parentElement.closest('table') && table.getClientRects().length > 0) html += cleanTable(table);
              });
              for (var i = 0; i < win.frames.length; i++) scan(win.frames[i], depth + 1);
            } catch (_) { unreadable = true; }
          }
          scan(window, 0);
          if (html.length > 2000000) return JSON.stringify({error: '页面过大，请打开独立课表页面'});
          if (!html) return JSON.stringify({error: '没有找到课表表格。请先登录并打开完整学期课表；此页面也可能需要学校专用适配。'});
          return JSON.stringify({html: html, unreadableFrames: unreadable});
        })();
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled") // Required for school login; no JavascriptInterface is installed.
@Composable
internal fun SchoolImportScreen(viewModel: MainViewModel, semesterId: Long) {
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("输入教务系统 HTTPS 网址，登录后打开完整学期课表。") }
    var loading by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf(viewModel.schoolBrowserUrl) }
    var closing by remember { mutableStateOf(false) }
    fun close(after: () -> Unit = {}) {
        if (closing) return
        closing = true
        webView?.stopLoading()
        webView?.loadUrl("about:blank")
        webView?.clearHistory()
        webView?.clearCache(true)
        webView?.removeAllViews()
        webView?.destroy()
        webView = null
        val finish = {
            viewModel.browserSessionStarted = false
            viewModel.schoolBrowserUrl = ""
            viewModel.showSchoolBrowser = false
            after()
        }
        try {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                finish()
            }
        } catch (_: Exception) { finish() } // The system WebView provider may be missing or disabled.
    }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else close() }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row {
                    IconButton(onClick = { close() }) { Icon(Icons.Default.Close, "关闭并清理登录信息") }
                    OutlinedTextField(address, { address = it }, Modifier.weight(1f), singleLine = true, label = { Text("学校教务网址") })
                    TextButton(onClick = {
                        val url = address.trim()
                        if (SchoolWebPolicy.allows(url)) { viewModel.schoolBrowserUrl = url; webView?.loadUrl(url) }
                        else status = "请输入完整 HTTPS 教务网址。HTTP 页面可用导出 HTML / CSV 的方式导入。"
                    }, enabled = !closing && !reading) { Text("打开") }
                }
                Text(status, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                Text("由你在学校页面登录；只在点击识别时读取课表表格，在本机解析。关闭会清理本次登录信息。", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                if (loading || reading || closing) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
                    try { WebView(context).apply {
                        webView = this
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.safeBrowsingEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (SchoolWebPolicy.allows(request.url.toString())) return false
                                status = "该链接需要外部应用或不安全连接。请使用学校 HTTPS 网页，或导入课表文件。"
                                return true
                            }
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                if (SchoolWebPolicy.allows(request.url.toString())) null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                handler.cancel(); status = "学校网页证书校验失败，连接已停止。可使用导出的课表文件。"; loading = false
                            }
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { loading = true }
                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                                if (SchoolWebPolicy.allows(url)) { viewModel.schoolBrowserUrl = url; address = url; status = "请进入含周次的学期课表，再点击下方识别。" }
                            }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                                if (request.isForMainFrame) { loading = false; status = "页面加载失败；请检查网址、校园网或学校 VPN 连接。" }
                            }
                        }
                        val initial = viewModel.schoolBrowserUrl
                        if (!viewModel.browserSessionStarted) {
                            viewModel.browserSessionStarted = true
                            WebStorage.getInstance().deleteAllData()
                            CookieManager.getInstance().removeAllCookies { if (alive.get() && SchoolWebPolicy.allows(initial)) loadUrl(initial) }
                        } else if (SchoolWebPolicy.allows(initial)) loadUrl(initial)
                    } } catch (_: Exception) {
                        webView = null
                        loading = false
                        status = "系统 WebView 不可用，请更新系统网页组件；也可以返回导入 HTML / CSV 文件。"
                        android.widget.TextView(context).apply { text = status; setPadding(24, 24, 24, 24) }
                    }
                })
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (webView?.canGoBack() == true) webView?.goBack() }, enabled = !reading && !closing) { Text("后退") }
                    Button(onClick = {
                        reading = true
                        webView?.evaluateJavascript(SchoolWebPolicy.captureScript) { encoded ->
                            if (!closing) close { viewModel.previewSchoolPage(encoded, semesterId) }
                        }
                    }, modifier = Modifier.weight(1f), enabled = !loading && !reading && !closing && webView != null && SchoolWebPolicy.allows(viewModel.schoolBrowserUrl)) { Text("识别课表并预览") }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { alive.set(false); webView?.apply { stopLoading(); removeAllViews(); destroy() }; webView = null }
    }
}
