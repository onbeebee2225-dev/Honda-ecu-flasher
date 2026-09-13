package com.example.hondaecuflasher

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LiveDataActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbSerialManager
    private lateinit var liveDataReader: LiveDataReader
    private var pollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)

        val tvRpm = findViewById<TextView>(R.id.tvRpm)
        val tvCoolant = findViewById<TextView>(R.id.tvCoolant)
        val tvThrottle = findViewById<TextView>(R.id.tvThrottle)
        val tvIgnition = findViewById<TextView>(R.id.tvIgnition)
        val tvStatus = findViewById<TextView>(R.id.tvLiveStatus)
        val btnStart = findViewById<Button>(R.id.btnStartLive)

        usbManager = UsbSerialManager(this)
        liveDataReader = LiveDataReader(usbManager).apply {
            onSnapshot = { snapshot ->
                runOnUiThread {
                    tvRpm.text = "รอบเครื่อง (RPM): ${snapshot.rpm ?: "-"}"
                    tvCoolant.text = "อุณหภูมิน้ำ/หัวเครื่อง: ${snapshot.coolantTempC ?: "-"} °C"
                    tvThrottle.text = "ตำแหน่งคันเร่ง: ${snapshot.throttlePercent ?: "-"} %"
                    tvIgnition.text = "องศาจุดระเบิด: ${snapshot.ignitionTimingDeg ?: "-"} °"
                }
            }
            onError = { message ->
                runOnUiThread { tvStatus.text = message }
            }
        }

        btnStart.setOnClickListener {
            if (pollingJob?.isActive == true) {
                pollingJob?.cancel()
                btnStart.text = "เริ่มอ่านข้อมูลสด"
                tvStatus.text = "หยุดอ่านข้อมูลแล้ว"
            } else {
                pollingJob = CoroutineScope(Dispatchers.IO).launch {
                    liveDataReader.startPolling()
                }
                btnStart.text = "หยุดอ่านข้อมูล"
                tvStatus.text = "กำลังอ่านข้อมูลสด..."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
        usbManager.close()
    }
}
