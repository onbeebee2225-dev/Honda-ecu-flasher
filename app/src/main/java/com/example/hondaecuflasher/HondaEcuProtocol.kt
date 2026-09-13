package com.example.hondaecuflasher

import android.hardware.usb.UsbDevice
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Honda ECU K-Line (ISO9141 variant) communication protocol.
 *
 * อ้างอิงจากโปรเจกต์โอเพนซอร์ส HondaECU (K-line reverse engineering)
 * และ Honda_Keihin_KLine_Protocol
 *
 * สถานะ: READ ONLY — ยังไม่เปิดใช้งานฟังก์ชันเขียน (write/flash) จนกว่าจะ
 * ทดสอบอ่านค่าได้เสถียรและยืนยัน checksum algorithm ถูกต้องแล้ว
 */
class HondaEcuProtocol(private val port: UsbSerialPort) {

    companion object {
        const val BAUD_RATE = 10400 // K-line มาตรฐานฮอนด้า ไม่ใช่ baud ปกติ
        const val INIT_BYTE = 0x00
        const val READ_TIMEOUT_MS = 2000
        const val TABLE_REQUEST_HEADER = 0x72 // ตัวอย่างจากเอกสารอ้างอิง
    }

    /**
     * ตั้งค่าพอร์ตให้ตรงกับสเปก K-line ของฮอนด้า
     */
    fun configurePort() {
        port.setParameters(
            BAUD_RATE,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE
        )
    }

    /**
     * ลำดับการปลุก ECU ให้พร้อมสื่อสาร (init sequence)
     * NOTE: ต้องทดสอบจับเวลาจริงกับ ECU รุ่นที่ใช้งาน เวลาที่ระบุเป็นค่าอ้างอิงเบื้องต้น
     */
    suspend fun initEcu(): Boolean {
        return try {
            port.setDTR(true)
            port.setRTS(true)
            delay(70) // ช่วง break ตาม ISO9141 fast-init โดยประมาณ
            port.setRTS(false)
            delay(25)

            // ส่ง init byte แล้วรอ ECU ตอบกลับ keybyte
            port.write(byteArrayOf(INIT_BYTE.toByte()), READ_TIMEOUT_MS)

            val response = ByteArray(64)
            val len = port.read(response, READ_TIMEOUT_MS)
            len > 0
        } catch (e: IOException) {
            false
        }
    }

    /**
     * ขอข้อมูลจาก ECU ตามหมายเลขตาราง (table id) เช่น 0x11, 0x17
     * ตามรูปแบบที่พบใน Honda_Keihin_KLine_Protocol: 72 05 71 [table] [checksum]
     */
    suspend fun requestDataTable(tableId: Int): ByteArray? {
        val frame = buildFrame(tableId)
        return try {
            port.write(frame, READ_TIMEOUT_MS)
            val response = ByteArray(64)
            val len = port.read(response, READ_TIMEOUT_MS)
            if (len > 0) response.copyOf(len) else null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * สร้าง frame คำสั่งพร้อม checksum
     * checksum ของ K-line ฮอนด้าโดยทั่วไปคือ two's complement ของผลรวมไบต์ก่อนหน้า
     */
    private fun buildFrame(tableId: Int): ByteArray {
        val body = byteArrayOf(
            TABLE_REQUEST_HEADER.toByte(),
            0x05,
            0x71,
            tableId.toByte()
        )
        val checksum = calculateChecksum(body)
        return body + checksum
    }

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (b in data) {
            sum += (b.toInt() and 0xFF)
        }
        // two's complement checksum — ต้องตรวจสอบกับ ECU จริงว่าตรงหรือไม่
        return ((0x100 - (sum and 0xFF)) and 0xFF).toByte()
    }

    /**
     * ตรวจสอบว่า response ที่ได้ผ่าน checksum หรือไม่
     */
    fun validateChecksum(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        val payload = data.copyOfRange(0, data.size - 1)
        val expected = data.last()
        return calculateChecksum(payload) == expected
    }

    // TODO: writeMemory() / flashRom() — จงใจยังไม่ implement
    // ต้องทดสอบ read + checksum ให้เสถียรก่อน และมีระบบ backup ROM
    // ก่อนเขียนทับ เพื่อป้องกัน ECU เสียหาย (bricked)
}
