package com.example.kotwebview

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.JsResult
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 웹뷰 인스턴스 생성 또는 레이아웃 바인딩
        webView = WebView(this)
        setContentView(webView)

        // 2. 웹뷰 설정 (WebSettings)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // 자바스크립트 활성화
        webSettings.domStorageEnabled = true // 로컬 스토리지 활성화
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true

        // 3. 웹 클라이언트 설정 (앱 내에서 링크 열기)
        webView.webViewClient = WebViewClient()

        // WebChromeClient 설정 (alert, confirm 대응)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
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
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("알림")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }
        }

        // 4. Javascript Interface 등록 (Web -> Native 호출용)
        webView.addJavascriptInterface(WebAppInterface(this, webView), "AndroidBridge")

        // 5. URL 로딩 (운영 서버/로컬 서버 주소)
//        webView.loadUrl("https://m.naver.com/")
//        webView.loadUrl("http://localhost:3000/")
        webView.loadUrl("http://146.56.103.154:8080/")

        // 6. 뒤로가기 처리 (앱 종료 방지)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    // 앱이 뒤로가기로 종료 안되도록 함
                    // 아무것도 하지 않음
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }
}
