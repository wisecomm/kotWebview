package com.example.kotwebview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DataWedgeReceiver(private val bridge: WebAppInterface) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action

        // DataWedge 설정에서 지정한 Action 이름과 일치해야 함
        if (action == "com.example.pda.ACTION") {
            // 바코드 데이터 추출 (DataWedge 표준 Extra key)
            val data = intent.getStringExtra("com.symbol.datawedge.data_string")

            if (data != null) {
                // 웹뷰 브릿지를 통해 JS로 데이터 전달
                bridge.sendScanResult(data)
            }
        }
    }
}

