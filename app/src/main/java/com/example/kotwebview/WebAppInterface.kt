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
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

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
