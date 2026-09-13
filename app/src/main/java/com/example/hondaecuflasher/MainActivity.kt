package com.example.hondaecuflasher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbSerialManager
    private lateinit var protocol: HondaEcuProtocol
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnFlash: Button

    private var remapBytes: ByteArray? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadBinFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        logText = findViewById(R.id.logText)
        progressBar = findViewById(R.id.progressBar)
        btnFlash = findViewById(R.id.btnFlash)

        usbManager = UsbSerialManager(this).apply {
            onLog = { appendLog(it) }
        }
        protocol = HondaEcuProtocol(usbManager).apply {
            onProgress = { step, percent, message ->
                runOnUiThread {
                    progressBar.progress = percent
                    statusText.text = "สถานะ: $step"
                    appendLog(message)
                }
            }
        }

        findViewById<Button>(R.id.btnConnect).setOnClickListener { connectUsb() }
        findViewById<Button>(R.id.btnPickFile).setOnClickListener { filePicker.launch("*/*") }
        btnFlash.setOnClickListener { startFlash() }
        findViewById<Button>(R.id.btnLiveData).setOnClickListener {
            startActivity(Intent(this, LiveDataActivity::class.java))
        }
    }

    private fun connectUsb() {
        val drivers = usbManager.listAvailableDrivers()
        if (drivers.isEmpty()) {
            appendLog("ไม่พบอุปกรณ์ USB-serial — ตรวจสอบสาย OTG และการเสียบสาย")
            return
        }
        val driver = drivers[0]
        usbManager.requestPermission(driver.device) { granted ->
            if (granted) {
                val ok = usbManager.openPort(driver)
                statusText.text = if (ok) "สถานะ: เชื่อมต่อแล้ว" else "สถานะ: เชื่อมต่อล้มเหลว"
            } else {
                appendLog("ผู้ใช้ปฏิเสธสิทธิ์การเข้าถึง USB")
            }
        }
    }

    private fun loadBinFile(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            withContext(Dispatchers.Main) {
                remapBytes = bytes
                if (bytes != null) {
                    appendLog("โหลดไฟล์สำเร็จ: ${bytes.size} bytes")
                    btnFlash.isEnabled = true
                } else {
                    appendLog("โหลดไฟล์ไม่สำเร็จ")
                }
            }
        }
    }

    private fun startFlash() {
        val data = remapBytes ?: run {
            appendLog("ยังไม่ได้เลือกไฟล์รีแมพ")
            return
        }
        appendLog("เริ่มกระบวนการ flash — ห้ามถอดสายจนกว่าจะเสร็จสิ้น")
        CoroutineScope(Dispatchers.IO).launch {
            protocol.flash(data)
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            logText.append("$message\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        usbManager.close()
    }
}
