package com.example.kotwebview

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // 1. 웹뷰 인스턴스 생성 또는 레이아웃 바인딩
        webView = WebView(this)
        setContentView(webView)

        // 2. WebViewManager를 통해 설정 및 이벤트 처리 위임
        val webViewManager = WebViewManager(this, webView)
        webViewManager.setup()

        // 3. URL 로딩 (운영 서버/로컬 서버 주소)
//        webView.loadUrl("https://m.naver.com/")
//        webView.loadUrl("http://localhost:3000/")
        webView.loadUrl("http://146.56.103.154:8080/")

        // 4. 뒤로가기 처리 (앱 종료 방지)
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
