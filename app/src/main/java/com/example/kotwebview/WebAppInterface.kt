package com.example.kotwebview

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    /** 1. Web -> Native: 토스트 메시지 띄우기 */
    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }

    /** 2. Web -> Native: 앱 종료 */
    @JavascriptInterface
    fun exitApp() {
        (mContext as? MainActivity)?.finish()
    }

    /** 4. Web -> Native: 서버 에러 처리 */
    @JavascriptInterface
    fun handleServerError(message: String) {
        if (mContext is MainActivity) {
            mContext.runOnUiThread {
                android.app.AlertDialog.Builder(mContext)
                    .setTitle("서버 오류")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ -> }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /** 3. Native -> Web: 바코드 데이터 전달용 함수 (내부 호출용) */
    fun sendScanResult(data: String) {
        webView.post {
            webView.evaluateJavascript("javascript:onScanResult('$data')", null)
        }
    }
}
