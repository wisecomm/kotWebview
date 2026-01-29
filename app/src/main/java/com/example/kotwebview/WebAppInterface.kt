package com.example.kotwebview

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class WebAppInterface(private val mContext: Context, private val webView: WebView) {

    /**
     * [Unified Bridge Architecture]
     * iOS와 Android의 통신 방식을 통일하기 위한 단일 진입점입니다.
     * Web에서 JSON 문자열을 보냅니다: { "action": "LOGOUT", "data": { ... } }
     */
    @JavascriptInterface
    fun postMessage(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val action = jsonObject.optString("action")
            val data = jsonObject.optJSONObject("data")
            val callbackId = jsonObject.optString("callbackId") // 응답이 필요한 경우 ID 수신

            when (action) {
                "LOGOUT" -> logout()
                "EXIT_APP" -> exitApp()
                "SHOW_TOAST" -> {
                    val message = data?.optString("message") ?: ""
                    showToast(message)
                }
                "ERROR_DIALOG" -> {
                    val message = data?.optString("message") ?: "오류가 발생했습니다."
                    handleServerError(message)
                }
                "GET_APP_VERSION" -> {
                    // 데이터 반환 예시
                    val versionInfo = JSONObject()
                    try {
                        val pInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
                        versionInfo.put("versionName", pInfo.versionName)
                        versionInfo.put("versionCode", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    sendResponse(callbackId, versionInfo)
                }
                "DELAYED_WORK" -> {
                    // 예: 별도 스레드에서 무언가 수행 (네트워크, DB 등)
                    Thread {
                        try {
                            Thread.sleep(3000) // 3초 대기 (오래 걸리는 작업)
                        } catch (e: InterruptedException) {}
                        // 작업 완료 후, 나중에 응답 전송!
                        // 메인 스레드에서 웹뷰를 호출해야 하므로 runOnUiThread 사용
                        (mContext as? MainActivity)?.runOnUiThread {
                            val resultData = JSONObject().put("status", "done")
                            sendResponse(callbackId, resultData)
                        }
                    }.start()
                }
                else -> Log.w("WebAppInterface", "Unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e("WebAppInterface", "Bridge Error: ${e.message}")
        }
    }

    /**
     * Web으로 비동기 응답을 보냅니다. (Request-Response 패턴)
     * Web에서 정의한 window.onNativeResponse(callbackId, dataString) 함수를 호출합니다.
     */
    private fun sendResponse(callbackId: String?, data: JSONObject) {
        if (callbackId.isNullOrEmpty()) return

        val responseString = data.toString()
        webView.post {
            // Android KitKat 이상에서는 evaluateJavascript 사용 권장
            val js = "javascript:if(window.onNativeResponse) { window.onNativeResponse('$callbackId', JSON.stringify($responseString)); }"
            webView.evaluateJavascript(js, null)
        }
    }

    /**
     * Native에서 Web으로 이벤트를 보냅니다. (Event-Listening 패턴)
     * 예: 푸시 알림 수신, 배터리 부족, 센서 데이터 등
     * Web에서 정의한 window.onNativeEvent(eventName, dataString) 함수를 호출합니다.
     */
    fun sendEvent(eventName: String, data: JSONObject) {
        val dataString = data.toString()
        webView.post {
            val js = "javascript:if(window.onNativeEvent) { window.onNativeEvent('$eventName', JSON.stringify($dataString)); }"
            webView.evaluateJavascript(js, null)
        }
    }


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

    /** 3. Web -> Native: 로그아웃 (쿠키 삭제) */
    @JavascriptInterface
    fun logout() {
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        Toast.makeText(mContext, "로그아웃 완료", Toast.LENGTH_SHORT).show()
    }

    /** 3-1. Web -> Native: 쿠키 동기화 강제 (로그인 성공 직후 호출) */
    @JavascriptInterface
    fun syncCookies() {
        android.webkit.CookieManager.getInstance().flush()
        // Log.d("WebAppInterface", "Cookie Sync Triggered from Web")
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

    /** 5. Web -> Native: Blob 파일 다운로드 (Base64) */
    @JavascriptInterface
    fun downloadBlob(base64: String, mimeType: String, fileName: String) {
        if (mContext is MainActivity) {
            mContext.runOnUiThread {
                try {
                    val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
                    
                    val finalFileName = if (fileName.isEmpty() || fileName == "null") {
                        "download_${System.currentTimeMillis()}.${getExtensionFromMimeType(mimeType)}"
                    } else {
                        fileName
                    }

                    // Fix: Infer MIME type from the filename extension
                    // This prevents MediaStore from appending ".txt" if the delivered mimeType is "text/plain" but the file is an image.
                    var finalMimeType = mimeType
                    val extension = finalFileName.substringAfterLast('.', "")
                    if (extension.isNotEmpty()) {
                        val typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                        if (typeFromExt != null) {
                            finalMimeType = typeFromExt
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, finalMimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }

                        val resolver = mContext.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.write(decodedBytes)
                            }
                            Toast.makeText(mContext, "다운로드 완료: $finalFileName", Toast.LENGTH_LONG).show()
                            showDownloadNotification(uri, finalMimeType, finalFileName)
                        } else {
                            Toast.makeText(mContext, "다운로드 실패: 파일 생성 불가", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Android 9 이하
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadDir, finalFileName)
                        FileOutputStream(file).use { outputStream ->
                            outputStream.write(decodedBytes)
                        }
                        Toast.makeText(mContext, "다운로드 완료: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        
                        // FileProvider 사용 (AndroidManifest에 provider 설정 필요할 수 있음. 일단 단순 경로로 시도하거나 스캔 유도)
                        // 여기서는 단순히 알림만 띄우거나, FileProvider URI를 생성해야 함.
                        // 편의상 Android 10 미만은 Toast만 유지하거나, FileProvider 설정이 되어있다고 가정.
                        // Uri.fromFile은 최신 안드로이드에서 crash 발생 가능. 
                        try {
                            val uri = FileProvider.getUriForFile(mContext, "${mContext.packageName}.provider", file)
                             showDownloadNotification(uri, finalMimeType, finalFileName)
                        } catch (e: Exception) {
                            Log.e("WebAppInterface", "FileProvider error", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebAppInterface", "Blob Download Failed", e)
                    Toast.makeText(mContext, "다운로드 중 오류 발생: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getExtensionFromMimeType(mimeType: String): String {
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            else -> "bin"
        }
    }

    /**
     * 다운로드 완료 알림 표시 (클릭 시 파일 열기)
     */
    private fun showDownloadNotification(uri: Uri, mimeType: String, fileName: String) {
        Log.d("WebAppInterface", "Attempting to show notification for: $fileName, uri: $uri")
        val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "download_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "File Download Notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // PendingIntent 생성 (Android 12+ 대응 FLAG_IMMUTABLE)
        val pendingIntent = PendingIntent.getActivity(
            mContext,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(mContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("다운로드 완료")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /** 3. Native -> Web: 바코드 데이터 전달용 함수 (내부 호출용) */
    fun sendScanResult(data: String) {
        webView.post {
            webView.evaluateJavascript("javascript:onScanResult('$data')", null)
        }
    }
}
