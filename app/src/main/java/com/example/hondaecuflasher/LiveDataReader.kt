package com.example.hondaecuflasher

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

data class LiveDataSnapshot(
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val throttlePercent: Int? = null,
    val ignitionTimingDeg: Int? = null,
    val raw: ByteArray = ByteArray(0)
)

class LiveDataReader(private val usb: UsbSerialManager) {

    var onSnapshot: ((LiveDataSnapshot) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private fun buildRequestFrame(): ByteArray {
        return ByteArray(0)
    }

    private fun parseResponse(response: ByteArray): LiveDataSnapshot {
        return LiveDataSnapshot(raw = response)
    }

    suspend fun startPolling(intervalMs: Long = 200) {
        val requestFrame = buildRequestFrame()
        if (requestFrame.isEmpty()) {
            onError?.invoke("ยังไม่ได้ตั้งค่า request frame สำหรับรุ่นนี้ (ดู TODO ใน LiveDataReader)")
            return
        }

        val buffer = ByteArray(64)
        while (coroutineContext.isActive) {
            usb.write(requestFrame)
            val len = usb.read(buffer, timeoutMs = 300)
            if (len > 0) {
                val response = buffer.copyOfRange(0, len)
                onSnapshot?.invoke(parseResponse(response))
            }
            delay(intervalMs)
        }
    }
}
