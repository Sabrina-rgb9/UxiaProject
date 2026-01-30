package com.sabrina.uxiaproject.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File
import java.util.*

import com.sabrina.uxiaproject.R


class BLEconnDialog(
    context: Context,
    private val device: BluetoothDevice,
    private val callback: BLEConnectionCallback
) : AlertDialog(context) {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false

    // UUIDs del ESP32 (ajusta según tu dispositivo)
    companion object {
        const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
    }

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)
    }

    init {
        setTitle("Conectant amb ESP32...")
        setMessage(device.name ?: device.address)
        setCancelable(false)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ble_connection)

        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        setButton(BUTTON_NEGATIVE, "Cancel·lar") { _, _ ->
            disconnect()
            callback.onConnectionCancelled()
            dismiss()
        }

        connectToDevice()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        statusText.text = "Conectant..."
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            Handler(Looper.getMainLooper()).post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        statusText.text = "Connectat! Cercant serveis..."
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        if (!isShowing) return@post
                        statusText.text = "Desconnectat"
                        callback.onConnectionFailed("Desconnectat")
                        dismiss()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Handler(Looper.getMainLooper()).post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    statusText.text = "Serveis trobats. Esperant imatge..."
                    enableNotifications(gatt)
                    callback.onConnectionSuccess(gatt)
                } else {
                    callback.onConnectionFailed("Error cercant serveis")
                    dismiss()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            val data = characteristic.value
            if (data.isNotEmpty()) {
                processReceivedData(data)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(UUID.fromString(SERVICE_UUID))
        val characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    private fun processReceivedData(data: ByteArray) {
        // Aquí procesas los datos recibidos del ESP32
        // Suponiendo que el ESP32 envía imágenes completas

        Handler(Looper.getMainLooper()).post {
            statusText.text = "Rebent imatge..."

            // Crear archivo temporal
            val tempFile = File.createTempFile("uxia_image_", ".jpg", context.cacheDir)
            tempFile.writeBytes(data)

            // Llamar al callback
            callback.onReceivedImage(tempFile)

            // Cerrar diálogo después de recibir
            Handler(Looper.getMainLooper()).postDelayed({
                dismiss()
            }, 1000)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestImage() {
        if (!isConnected) return

        // Enviar comando al ESP32 para que envíe imagen
        val command = "GET_IMAGE".toByteArray()
        val service = bluetoothGatt?.getService(UUID.fromString(SERVICE_UUID))
        val characteristic = service?.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))

        characteristic?.value = command
        bluetoothGatt?.writeCharacteristic(characteristic)

        statusText.text = "Sol·licitant imatge..."
    }

    override fun dismiss() {
        disconnect()
        super.dismiss()
    }
}