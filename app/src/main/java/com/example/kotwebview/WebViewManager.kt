package com.example.kotwebview

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class WebViewManager(
    private val context: Context,
    private val webView: WebView,
    private val onFileChooser: (ValueCallback<Array<Uri>>?) -> Unit
) {

    fun setup() {
        // 1. 웹뷰 설정 (WebSettings)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // 자바스크립트 활성화
        webSettings.domStorageEnabled = true // 로컬 스토리지 활성화
        webSettings.loadWithOverviewMode = true
        webSettings.useWideViewPort = true
        
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // 쿠키 설정 (로그인 세션 유지)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 2. 웹 클라이언트 설정 (앱 내에서 링크 열기 및 에러 감지)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d("WebViewManager", "Page Started: $url")
            }

            override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e("WebViewManager", "Page Error: ${error?.description}, Code: ${error?.errorCode}")

                if (request?.isForMainFrame == true) {
                    AlertDialog.Builder(context)
                        .setTitle("오류")
                        .setMessage("페이지를 로드할 수 없습니다.\n${error?.description}")
                        .setPositiveButton("확인", null)
                        .show()
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("WebViewManager", "HTTP Error: ${errorResponse?.statusCode}")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // JSON 에러 응답 감지 (status: error)
                view?.evaluateJavascript(
                    """
                    (function() {
                        // 1. Error Handling
                        try {
                            var content = document.body.innerText;
                            if (content.trim().startsWith('{"status":"error"')) {
                                var json = JSON.parse(content);
                                window.AndroidBridge.handleServerError(json.message);
                            }
                        } catch(e) {}

                        // 2. Blob Revocation Blocker
                        // 웹사이트가 다운로드 직후 Blob을 revoke하면 다운로드가 실패하므로 이를 방지함
                        console.log("WebViewManager: Injecting Revoke Blocker");
                        window.URL.revokeObjectURL = function(url) {
                             console.log("WebViewManager: Blocked revokeObjectURL for " + url);
                        };

                        // 3. Anchor Click Hook (Filename capture)
                        // 동적으로 생성되어 클릭되는 링크의 파일명을 캡처하기 위함
                        if (!window.__downloadMap) {
                            window.__downloadMap = {};
                            var oldClick = HTMLAnchorElement.prototype.click;
                            HTMLAnchorElement.prototype.click = function() {
                                var href = this.href;
                                var download = this.download;
                                if (href && download) {
                                    window.__downloadMap[href] = download;
                                    console.log("WebViewManager: Captured filename for " + href + " -> " + download);
                                }
                                oldClick.apply(this, arguments);
                            };
                        }
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

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d("WebViewManager", "onShowFileChooser called")
                onFileChooser(filePathCallback)
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        // 4. Javascript Interface 등록 (Web -> Native 호출용)
        webView.addJavascriptInterface(WebAppInterface(context, webView), "AndroidBridge")

        // 5. 다운로드 리스너 설정
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            Log.d("WebViewManager", "Download info: url=$url, mimetype=$mimetype")

            if (url.startsWith("blob:")) {
                Log.d("WebViewManager", "Blob URL detected. Initiating JS handling.")
                
                val js = """
                    (function() {
                        console.log("Blob JS: Starting download for URL: $url");
                        
                        // Try to get filename from the captured map or active element
                        var bestFilename = "";
                        if (window.__downloadMap && window.__downloadMap['$url']) {
                            bestFilename = window.__downloadMap['$url'];
                            console.log("Blob JS: Found capture filename: " + bestFilename);
                        } else {
                            var clickedLink = document.activeElement;
                            if (clickedLink && clickedLink.tagName !== 'A') {
                                clickedLink = clickedLink.closest('a');
                            }
                            if (clickedLink) {
                                bestFilename = clickedLink.getAttribute('download');
                            }
                        }
                        console.log("Blob JS: Final detected filename: " + bestFilename);

                        fetch('$url')
                            .then(function(response) {
                                if (!response.ok) {
                                    throw new Error("HTTP error, status = " + response.status);
                                }
                                console.log("Blob JS: Fetch Success");
                                return response.blob();
                            })
                            .then(function(blob) {
                                var reader = new FileReader();
                                reader.readAsDataURL(blob);
                                reader.onloadend = function() {
                                    console.log("Blob JS: FileReader Complete");
                                    var base64data = reader.result;
                                    var content = base64data.split(",")[1];
                                    if(window.AndroidBridge) {
                                        console.log("Blob JS: Calling AndroidBridge");
                                        // Use the actual blob type if available, otherwise fallback to the listener's mimetype
                                        var finalMimeType = blob.type.length > 0 ? blob.type : '$mimetype';
                                        
                                        // Use the captured filename if available, otherwise fallback to contentDisposition
                                        var finalFilename = bestFilename || '$contentDisposition';
                                        
                                        console.log("Blob JS: Final MimeType: " + finalMimeType + ", Filename: " + finalFilename);
                                        window.AndroidBridge.downloadBlob(content, finalMimeType, finalFilename);
                                    } else {
                                        console.error("Blob JS: AndroidBridge not found!");
                                    }
                                }
                            })
                            .catch(function(error) {
                                console.error("Blob JS: Fetch Error: " + error.message);
                            });
                    })();
                """.trimIndent()
                
                webView.evaluateJavascript(js, null)
                return@setDownloadListener
            }
            if (url.startsWith("data:")) {
                Toast.makeText(context, "Data URL 다운로드는 별도 구현이 필요합니다.", Toast.LENGTH_LONG).show()
                Log.e("WebViewManager", "Data URL download not strictly supported by DownloadManager.")
                return@setDownloadListener
            }

            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                // 쿠키 설정 (로그인 세션 유지 등)
                val cookies = CookieManager.getInstance().getCookie(url)
                request.addRequestHeader("cookie", cookies)
                request.addRequestHeader("User-Agent", userAgent)

                request.setDescription("Downloading file...")
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                request.setTitle(fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // 공용 다운로드 폴더에 저장
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                
                Toast.makeText(context, "다운로드를 시작합니다.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("WebViewManager", "Download failed", e)
                Toast.makeText(context, "다운로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
