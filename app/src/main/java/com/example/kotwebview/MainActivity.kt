package com.example.kotwebview

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("MainActivity", "File Chooser Result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            val intent = result.data
            var results: Array<Uri>? = null
            if (intent != null) {
                val dataString = intent.dataString
                val clipData = intent.clipData

                if (clipData != null) {
                    results = Array(clipData.itemCount) { i ->
                        clipData.getItemAt(i).uri
                    }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            Log.d("MainActivity", "File Selected: $results")
            fileUploadCallback?.onReceiveValue(results)
        } else {
            Log.d("MainActivity", "File Selection Cancelled")
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // 1. 웹뷰 인스턴스 생성 및 레이아웃 설정
        webView = WebView(this)
        setContentView(webView)

        // 2. WebViewManager 설정
        val webViewManager = WebViewManager(this, webView) { callback ->
            Log.d("MainActivity", "Launching File Chooser Intent")
            fileUploadCallback = callback
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*" // 모든 파일 타입
            try {
                fileChooserLauncher.launch(Intent.createChooser(intent, "File Chooser"))
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch chooser", e)
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = null
            }
        }
        webViewManager.setup()

        // 3. URL 로딩
        webView.loadUrl("http://158.180.67.194/")
        // 맥 접속 주소 (adb 연결 시) : ipconfig getifaddr en0
        //  webView.loadUrl("http://192.168.2.7:3000/")

        // 4. 뒤로가기 처리
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } 
                // 웹뷰 히스토리가 없으면 기본 동작(종료)을 하지 않음 (기획 의도에 따라 변경 가능)
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 갈 때 쿠키를 확실하게 저장
        android.webkit.CookieManager.getInstance().flush()
    }
}
