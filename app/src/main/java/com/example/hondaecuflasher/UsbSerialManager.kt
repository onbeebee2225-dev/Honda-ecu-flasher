package com.example.hondaecuflasher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class UsbSerialManager(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.hondaecuflasher.USB_PERMISSION"
        const val DEFAULT_BAUD_RATE = 10400
    }

    private var port: UsbSerialPort? = null
    var onLog: ((String) -> Unit)? = null

    fun listAvailableDrivers(): List<UsbSerialDriver> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    }

    fun requestPermission(device: UsbDevice, onResult: (granted: Boolean) -> Unit) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(device)) {
            onResult(true)
            return
        }

        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_MUTABLE
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    context.unregisterReceiver(this)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onResult(granted)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        manager.requestPermission(device, permissionIntent)
    }

    fun openPort(driver: UsbSerialDriver, baudRate: Int = DEFAULT_BAUD_RATE): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = manager.openDevice(driver.device) ?: run {
            onLog?.invoke("เปิดอุปกรณ์ USB ไม่สำเร็จ (ไม่มีสิทธิ์ หรืออุปกรณ์ไม่รองรับ)")
            return false
        }

        val serialPort = driver.ports[0]
        serialPort.open(connection)
        serialPort.setParameters(
            baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE
        )
        port = serialPort
        onLog?.invoke("เปิดพอร์ตสำเร็จที่ $baudRate baud")
        return true
    }

    fun write(data: ByteArray, timeoutMs: Int = 1000) {
        port?.write(data, timeoutMs)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = 1000): Int {
        return port?.read(buffer, timeoutMs) ?: -1
    }

    fun close() {
        port?.close()
        port = null
    }
}
