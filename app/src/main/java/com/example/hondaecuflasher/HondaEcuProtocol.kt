package com.example.hondaecuflasher

class HondaEcuProtocol(private val usb: UsbSerialManager) {

    enum class Step { IDLE, INIT, SECURITY_ACCESS, ERASE, WRITE, VERIFY, DONE, ERROR }

    var onProgress: ((Step, percent: Int, message: String) -> Unit)? = null

    private fun initSequence(): Boolean {
        onProgress?.invoke(Step.INIT, 0, "เริ่มต้นการเชื่อมต่อกับ ECU...")
        onProgress?.invoke(Step.ERROR, 0, "ยังไม่ได้ตั้งค่า init sequence สำหรับรุ่นนี้")
        return false
    }

    private fun securityAccess(seed: ByteArray): ByteArray? {
        onProgress?.invoke(Step.SECURITY_ACCESS, 10, "กำลังขอสิทธิ์เขียนข้อมูล (seed-key)...")
        return null
    }

    private fun eraseFlash(): Boolean {
        onProgress?.invoke(Step.ERASE, 20, "กำลังลบข้อมูลเดิมใน ECU...")
        return false
    }

    private fun writeFlash(binData: ByteArray): Boolean {
        val blockSize = 256
        var offset = 0
        while (offset < binData.size) {
            val end = minOf(offset + blockSize, binData.size)
            val block = binData.copyOfRange(offset, end)
            val percent = 20 + (offset * 60 / binData.size)
            onProgress?.invoke(Step.WRITE, percent, "เขียนข้อมูล ${offset}/${binData.size} bytes")
            offset = end
        }
        return false
    }

    private fun verifyFlash(binData: ByteArray): Boolean {
        onProgress?.invoke(Step.VERIFY, 90, "กำลังตรวจสอบความถูกต้องของข้อมูล...")
        return false
    }

    fun flash(binData: ByteArray) {
        if (!initSequence()) return
        if (!eraseFlash()) return
        if (!writeFlash(binData)) return
        if (!verifyFlash(binData)) return
        onProgress?.invoke(Step.DONE, 100, "Flash สำเร็จ")
    }
}
