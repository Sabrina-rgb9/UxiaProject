package com.sabrina.uxiaproject.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.io.OutputStream

import com.sabrina.uxiaproject.R


class BLEconnDialog(
    context: Context,
    private val device: android.bluetooth.BluetoothDevice,
    private val callback: BLEConnectionCallback
) : AlertDialog(context) {

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false
    private var imageData: ByteArray = byteArrayOf()
    private var imageSize: Int = 0
    private var bytesReceived: Int = 0

    // UUIDs del ESP32
    companion object {
        const val ESP32_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val ESP32_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
        const val COMMAND_REQUEST_IMAGE = "IMG_REQ"
    }

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)
    }

    init {
        setTitle("📡 Connectant amb ESP32")
        setMessage("Dispositiu: ${device.name ?: device.address}")
        setCancelable(true)

        setOnCancelListener {
            disconnect()
            callback.onConnectionCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar el layout personalizado
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_ble_connection, null)
        setView(view)

        // Inicializar vistas
        progressBar = view.findViewById(R.id.progressBar)
        statusText = view.findViewById(R.id.statusText)
        deviceNameText = view.findViewById(R.id.deviceNameText)

        deviceNameText.text = device.name ?: device.address

        // Configurar botones
        setButton(BUTTON_NEGATIVE, "Cancel·lar") { _, _ ->
            disconnect()
            callback.onConnectionCancelled()
            dismiss()
        }

        setButton(BUTTON_POSITIVE, "Sol·licitar Imatge") { _, _ ->
            requestImageFromESP32()
        }

        // Ocultar botón positivo inicialmente
        getButton(BUTTON_POSITIVE).visibility = android.view.View.GONE

        // Iniciar conexión
        connectToDevice()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        statusText.text = "🔗 Conectant..."
        progressBar.isIndeterminate = true

        bluetoothGatt = device.connectGatt(context, false, gattCallback)

        // Timeout de conexión
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isConnected && isShowing) {
                statusText.text = "⏰ Timeout de connexió"
                callback.onConnectionFailed("Timeout de connexió")
                dismiss()
            }
        }, 30000)
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
                        statusText.text = "✅ Connectat! Cercant serveis..."
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        if (isShowing) {
                            statusText.text = "❌ Desconnectat"
                            callback.onConnectionFailed("Desconnectat")
                            dismiss()
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            Handler(Looper.getMainLooper()).post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    statusText.text = "🔍 Serveis trobats"

                    if (enableNotifications(gatt)) {
                        statusText.text = "✅ Configurat"
                        progressBar.isIndeterminate = false
                        progressBar.progress = 0

                        getButton(BUTTON_POSITIVE).visibility = android.view.View.VISIBLE
                        callback.onConnectionSuccess(gatt)
                    } else {
                        callback.onConnectionFailed("Servei no trobat")
                        dismiss()
                    }
                } else {
                    callback.onConnectionFailed("Error: $status")
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
    private fun enableNotifications(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(UUID.fromString(ESP32_SERVICE_UUID))
        if (service == null) return false

        val characteristic = service.getCharacteristic(UUID.fromString(ESP32_CHARACTERISTIC_UUID))
        if (characteristic == null) return false

        gatt.setCharacteristicNotification(characteristic, true)

        val descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        return true
    }

    private fun processReceivedData(data: ByteArray) {
        Handler(Looper.getMainLooper()).post {
            if (bytesReceived == 0 && data.size >= 4) {
                // Primer paquete: tamaño
                imageSize = byteArrayToInt(data.sliceArray(0..3))
                imageData = ByteArray(imageSize)
                bytesReceived = 0

                if (data.size > 4) {
                    val length = data.size - 4
                    System.arraycopy(data, 4, imageData, 0, length)
                    bytesReceived = length
                }

                progressBar.max = imageSize
                progressBar.progress = bytesReceived
                updateProgressText()

            } else {
                // Paquetes siguientes
                val remaining = imageSize - bytesReceived
                val toCopy = minOf(data.size, remaining)

                System.arraycopy(data, 0, imageData, bytesReceived, toCopy)
                bytesReceived += toCopy

                progressBar.progress = bytesReceived
                updateProgressText()

                if (bytesReceived >= imageSize) {
                    statusText.text = "✅ Imatge rebuda!"
                    saveImageToGallery()
                }
            }
        }
    }

    private fun byteArrayToInt(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF shl 24) or
                (bytes[1].toInt() and 0xFF shl 16) or
                (bytes[2].toInt() and 0xFF shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun updateProgressText() {
        val percent = if (imageSize > 0) (bytesReceived * 100) / imageSize else 0
        statusText.text = "📥 Rebot: $percent% ($bytesReceived/$imageSize bytes)"
    }

    @SuppressLint("MissingPermission")
    private fun requestImageFromESP32() {
        if (!isConnected) {
            statusText.text = "❌ No connectat"
            return
        }

        val service = bluetoothGatt?.getService(UUID.fromString(ESP32_SERVICE_UUID))
        val characteristic = service?.getCharacteristic(UUID.fromString(ESP32_CHARACTERISTIC_UUID))

        if (characteristic != null) {
            imageData = byteArrayOf()
            imageSize = 0
            bytesReceived = 0

            val command = COMMAND_REQUEST_IMAGE.toByteArray()
            characteristic.value = command
            bluetoothGatt?.writeCharacteristic(characteristic)

            statusText.text = "📤 Sol·licitant imatge..."
            progressBar.progress = 0
            getButton(BUTTON_POSITIVE).visibility = android.view.View.GONE
        }
    }

    private fun saveImageToGallery() {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "UXIA_${timeStamp}.jpg"

            // Crear directorio Pictures/UXIA si no existe
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val uxiaDir = File(picturesDir, "UXIA")

            if (!uxiaDir.exists()) {
                uxiaDir.mkdirs()
            }

            val imageFile = File(uxiaDir, fileName)

            // Guardar imagen
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }

            // Notificar a la galería
            val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val contentUri = android.net.Uri.fromFile(imageFile)
            mediaScanIntent.data = contentUri
            context.sendBroadcast(mediaScanIntent)

            statusText.text = "💾 Guardat: $fileName"

            // Llamar callback
            callback.onReceivedImage(imageFile)

            // Cerrar después de 3 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                dismiss()
            }, 3000)

        } catch (e: Exception) {
            statusText.text = "❌ Error: ${e.message}"
            Toast.makeText(context, "Error guardant imatge", Toast.LENGTH_SHORT).show()
        }
    }

    override fun dismiss() {
        disconnect()
        super.dismiss()
    }
}