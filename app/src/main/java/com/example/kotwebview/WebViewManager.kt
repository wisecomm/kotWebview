package com.example.kotwebview

import android.app.AlertDialog
import android.content.Context
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class WebViewManager(private val context: Context, private val webView: WebView) {

    fun setup() {
        // 1. 웹뷰 설정 (WebSettings)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // 자바스크립트 활성화
        webSettings.domStorageEnabled = true // 로컬 스토리지 활성화
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        // 2. 웹 클라이언트 설정 (앱 내에서 링크 열기 및 에러 감지)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // JSON 에러 응답 감지 (status: error)
                view?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            var content = document.body.innerText;
                            if (content.trim().startsWith('{"status":"error"')) {
                                var json = JSON.parse(content);
                                window.AndroidBridge.handleServerError(json.message);
                            }
                        } catch(e) {}
                    })();
                    """.trimIndent(), null
                )
            }
        }

        // 3. WebChromeClient 설정 (alert, confirm 대응)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle("알림")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ -> result?.confirm() }
                    .setNegativeButton("취소") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(context)
                    .setTitle("알림")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        // 4. Javascript Interface 등록 (Web -> Native 호출용)
        webView.addJavascriptInterface(WebAppInterface(context, webView), "AndroidBridge")
    }
}
