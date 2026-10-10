package kr.co.softone.kotwebview

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var locationPermissionCallback: ((Boolean) -> Unit)? = null

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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val isGranted = fineGranted || coarseGranted
        Log.d("MainActivity", "Location Permission Result: fine=$fineGranted, coarse=$coarseGranted")
        if (isGranted) {
            checkLocationServiceEnabled()
        }
        locationPermissionCallback?.invoke(isGranted)
        locationPermissionCallback = null
    }

    fun requestLocationPermission(onResult: (Boolean) -> Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            checkLocationServiceEnabled()
            onResult(true)
        } else {
            locationPermissionCallback = onResult
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun checkLocationServiceEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

        if (!isGpsEnabled && !isNetworkEnabled) {
            AlertDialog.Builder(this)
                .setTitle("위치 서비스 필요")
                .setMessage("현재 위치를 확인하려면 기기의 위치(GPS) 설정을 켜주세요.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        webView.loadUrl("https://oneday-trip.vercel.app")

        // 4. 뒤로가기 처리
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 갈 때 쿠키를 확실하게 저장
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
