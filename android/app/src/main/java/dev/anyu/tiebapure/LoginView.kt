package dev.anyu.tiebapure

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable fun LoginView(vm: AppViewModel, done: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    Column(Modifier.fillMaxSize()) {
        Text("请在百度页面完成登录，再点击验证登录。登录资料仅加密保存在本机。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        AndroidView(modifier = Modifier.weight(1f), factory = { context ->
            WebView(context).apply {
                webView = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !Policy.loginUrl(request.url.toString())
                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) { handler.cancel(); vm.report("登录页面证书验证失败") }
                }
                loadUrl("https://wappass.baidu.com/passport/?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine")
            }
        })
        Button(modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !vm.loading, onClick = {
            val cookies = mutableMapOf<String, String>()
            for (host in listOf("https://wappass.baidu.com", "https://baidu.com", "https://tieba.baidu.com")) {
                CookieManager.getInstance().getCookie(host).orEmpty().split(';').forEach { item ->
                    val pair = item.trim().split('=', limit = 2)
                    if (pair.size == 2 && pair[0] in setOf("BDUSS", "BDUSS_BFESS", "STOKEN")) cookies[pair[0]] = pair[1]
                }
            }
            vm.completeLogin(cookies["BDUSS"] ?: cookies["BDUSS_BFESS"].orEmpty(), cookies["STOKEN"].orEmpty(), done)
        }) { Text(if (vm.loading) "正在验证…" else "验证登录") }
    }
    DisposableEffect(Unit) { onDispose { webView?.stopLoading(); webView?.destroy(); webView = null } }
}
