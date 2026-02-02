package com.sabrina.uxiaproject.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.sabrina.uxiaproject.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class BLEconnDialog(
    context: Context,
    private val device: android.bluetooth.BluetoothDevice,
    private val callback: BLEConnectionCallback
) : AlertDialog(context) {

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)
    }

    // UUIDs del ESP32
    companion object {
        const val ESP32_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
        const val ESP32_CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
    }

    // Views
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var isConnecting = false
    private val CONNECTION_TIMEOUT = 15000L // 15 segundos

    // Variables para recepción de imagen (COMO LA APP DE REFERENCIA)
    private val receivedData = ByteArrayOutputStream()
    private var totalSize = 0
    private var isReceiving = false
    private var bytesReceived = 0
    private val RECEIVE_TIMEOUT = 30000L // 30 segundos
    private var packetCount = 0
    private var lastPacketTime = 0L

    // Timeouts
    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            statusText.text = "Timeout de conexión"
            disconnect()
            callback.onConnectionFailed("Timeout de conexión")
            dismiss()
        }
    }

    private val receiveTimeoutRunnable = Runnable {
        if (isReceiving) {
            statusText.text = "Timeout en recepción"
            Log.e("BLE", "Timeout en recepción de imagen")
            resetPhotoTransfer()
            Toast.makeText(context, "Timeout recibiendo imagen", Toast.LENGTH_SHORT).show()
        }
    }

    init {
        setTitle("Conectando con ESP32")
        setMessage("Dispositivo: ${device.address}")
        setCancelable(true)
        setOnCancelListener {
            disconnect()
            callback.onConnectionCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_ble_connection, null)
        setView(view)

        // Inicializar vistas
        progressBar = view.findViewById(R.id.progressBar)
        statusText = view.findViewById(R.id.statusText)
        deviceNameText = view.findViewById(R.id.deviceNameText)

        deviceNameText.text = "ESP32\n${device.address}"

        // Configurar botones
        setButton(BUTTON_NEGATIVE, "Cancelar") { _, _ ->
            disconnect()
            callback.onConnectionCancelled()
            dismiss()
        }

        // Ocultar botón positivo (la referencia no lo tiene)
        setButton(BUTTON_POSITIVE, "") { _, _ -> }

        // Iniciar conexión
        connectToDevice()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        if (isConnecting) return

        isConnecting = true
        statusText.text = "Conectando..."
        progressBar.isIndeterminate = true

        // Configurar timeout
        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        // Conectar usando aplicación context
        try {
            bluetoothGatt = device.connectGatt(context.applicationContext, false, gattCallback)
            Log.d("BLE", "Iniciando conexión con ${device.address}")
        } catch (e: Exception) {
            statusText.text = "Error de conexión"
            Toast.makeText(context, "Error al conectar: ${e.message}", Toast.LENGTH_SHORT).show()
            callback.onConnectionFailed("Error de conexión: ${e.message}")
            dismiss()
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        handler.removeCallbacks(receiveTimeoutRunnable)

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e("BLE", "Error al desconectar: ${e.message}")
        }

        bluetoothGatt = null
        isConnected = false
        isConnecting = false
        isReceiving = false

        Log.d("BLE", "Desconectado")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        // Cancelar timeout de conexión
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        isConnected = true

                        statusText.text = "¡Conectado! Buscando servicios..."
                        Log.d("BLE", "Conectado al dispositivo")

                        // Solicitar MTU más grande
                        try {
                            gatt.requestMtu(517)
                        } catch (e: Exception) {
                            Log.e("BLE", "Error solicitando MTU")
                        }

                        // Descubrir servicios
                        handler.postDelayed({
                            try {
                                gatt.discoverServices()
                                Log.d("BLE", "Descubriendo servicios...")
                            } catch (e: Exception) {
                                statusText.text = "Error descubriendo servicios"
                                callback.onConnectionFailed("Error descubriendo servicios")
                            }
                        }, 500)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        isConnected = false

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            statusText.text = "Desconectado"
                            Log.d("BLE", "Desconectado normalmente")
                        } else {
                            val errorMsg = when (status) {
                                0x08 -> "Timeout"
                                0x13 -> "Terminado por host local"
                                0x16 -> "Terminado por host remoto"
                                0x3E -> "No conectado"
                                else -> "Error: 0x${status.toString(16).uppercase(Locale.US)}"
                            }
                            statusText.text = "Error: $errorMsg"
                            Log.e("BLE", "Error de conexión: $errorMsg")
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "MTU cambiado a: $mtu bytes")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    statusText.text = "Servicios encontrados"
                    Log.d("BLE", "Servicios descubiertos")

                    try {
                        // Buscar nuestro servicio ESP32
                        val service = gatt.getService(UUID.fromString(ESP32_SERVICE_UUID))
                        if (service != null) {
                            Log.d("BLE", "✓ Servicio ESP32 encontrado")
                            val characteristic = service.getCharacteristic(UUID.fromString(ESP32_CHARACTERISTIC_UUID))
                            if (characteristic != null) {
                                Log.d("BLE", "✓ Característica de imagen encontrada")

                                // Habilitar notificaciones
                                gatt.setCharacteristicNotification(characteristic, true)

                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
                                )
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)

                                    statusText.text = "¡Listo para recibir imágenes!"
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = 0

                                    callback.onConnectionSuccess(gatt)
                                    Log.d("BLE", "✓ Configuración BLE completada")

                                } else {
                                    val error = "Descriptor no encontrado"
                                    statusText.text = "Error: $error"
                                    callback.onConnectionFailed(error)
                                }
                            } else {
                                val error = "Característica no encontrada"
                                statusText.text = "Error: $error"
                                callback.onConnectionFailed(error)
                            }
                        } else {
                            val error = "Servicio ESP32 no encontrado"
                            statusText.text = "Error: $error"
                            callback.onConnectionFailed(error)
                        }
                    } catch (e: Exception) {
                        statusText.text = "Error de configuración"
                        callback.onConnectionFailed("Error de configuración: ${e.message}")
                    }
                } else {
                    val error = "Error descubriendo servicios: $status"
                    statusText.text = "Error: $error"
                    callback.onConnectionFailed(error)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            if (data.isNotEmpty()) {
                handler.post {
                    handleIncomingData(data)  // Usar el mismo nombre que la app de referencia
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // No necesitamos escribir en este caso
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Descriptor configurado correctamente")
                }
            }
        }
    }

    // MÉTODO handleIncomingData COMO LA APP DE REFERENCIA
    private fun handleIncomingData(data: ByteArray) {
        packetCount++
        lastPacketTime = System.currentTimeMillis()

        Log.d("BLE", "Paquete $packetCount recibido: ${data.size} bytes")
        Log.d("BLE", "Primeros bytes: ${data.take(4).joinToString("") { "%02X".format(it) }}")

        // Verificar si es paquete de finalización (COMO LA REFERENCIA)
        if (data.size == 4 && data.contentEquals(
                byteArrayOf(
                    0xFF.toByte(),
                    0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
                )
            )
        ) {
            if (isReceiving && receivedData.size() > 0) {
                completePhotoTransfer()
            } else {
                statusText.text = "Finalización sin datos"
            }
            return
        }

        // Primer paquete (puede ser el tamaño) - COMO LA REFERENCIA
        if (!isReceiving && data.size == 4) {
            // Intentar interpretar como tamaño de 32 bits
            try {
                totalSize = (data[0].toInt() and 0xFF) +
                        ((data[1].toInt() and 0xFF) shl 8) +
                        ((data[2].toInt() and 0xFF) shl 16) +
                        ((data[3].toInt() and 0xFF) shl 24)

                isReceiving = true
                receivedData.reset()
                bytesReceived = 0

                statusText.text = "Recibiendo foto ($totalSize bytes)..."
                progressBar.max = totalSize
                progressBar.progress = 0

                Log.d("BLE", "Tamaño anunciado: $totalSize bytes")

                // Iniciar timeout
                startReceiveTimeout()

            } catch (e: Exception) {
                Log.e("BLE", "Error interpretando tamaño: ${e.message}")
            }
            return
        }

        // Si estamos recibiendo, agregar datos - COMO LA REFERENCIA
        if (isReceiving) {
            receivedData.write(data)
            bytesReceived += data.size

            val currentSize = receivedData.size()
            progressBar.progress = currentSize

            // Actualizar estado cada ciertos paquetes
            if (packetCount % 10 == 0 || currentSize == totalSize) {
                val percent = if (totalSize > 0)
                    (currentSize * 100) / totalSize else 0

                statusText.text =
                    "Recibiendo: $currentSize/$totalSize bytes ($percent%)"

                Log.d("BLE", "Progreso: $currentSize/$totalSize ($percent%)")
            }

            // Reiniciar timeout con cada paquete
            resetReceiveTimeout()

            // Si hemos llegado al tamaño esperado, completar
            if (totalSize > 0 && currentSize >= totalSize) {
                completePhotoTransfer()
            }
        }
    }

    private fun startReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        handler.postDelayed(receiveTimeoutRunnable, RECEIVE_TIMEOUT)
    }

    private fun resetReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        handler.postDelayed(receiveTimeoutRunnable, RECEIVE_TIMEOUT)
    }

    // MÉTODO completePhotoTransfer COMO LA APP DE REFERENCIA
    private fun completePhotoTransfer() {
        val finalSize = receivedData.size()

        handler.post {
            statusText.text = "Foto recibida: $finalSize bytes"
            progressBar.progress = finalSize

            // EXACTAMENTE COMO LA APP DE REFERENCIA
            val dataStr = receivedData.toString().trim()
            Log.v("FOTO", "Datos recibidos como string")

            try {
                val decodedData = Base64.decode(dataStr, Base64.DEFAULT)
                savePhoto(decodedData)
                Log.v("BT", "Foto recibida: $finalSize bytes")
            } catch (e: Exception) {
                Log.v("ERROR", "Error en descodificación base64: ${e.message}")
                // Intentar guardar directo como último recurso
                savePhoto(receivedData.toByteArray())
            }

            // Reset
            resetPhotoTransfer()
        }
    }

    // MÉTODO savePhoto COMO LA APP DE REFERENCIA
    private fun savePhoto(imageData: ByteArray) {
        try {
            val timestamp = System.currentTimeMillis()
            val filename = "UXIA_${timestamp}.jpg"

            // Guardar al directorio Pictures/UXIA
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val uxiaDir = File(picturesDir, "UXIA")

            if (!uxiaDir.exists()) {
                uxiaDir.mkdirs()
            }

            val imageFile = File(uxiaDir, filename)
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
                fos.flush()
            }

            Log.d("Photo", "Foto guardada: ${imageFile.absolutePath}")
            Log.d("Photo", "Tamaño del archivo: ${imageFile.length()} bytes")

            // Verificar si es JPEG válido
            val isValidJpeg = imageData.size >= 2 &&
                    imageData[0].toInt() == 0xFF &&
                    imageData[1].toInt() == 0xD8

            if (isValidJpeg) {
                Log.d("Photo", "✓ JPEG válido")
            } else {
                Log.d("Photo", "⚠ Los datos NO son JPEG válido")
                // Mostrar primeros bytes para diagnóstico
                val firstBytes = imageData.take(10).joinToString("") { "%02X".format(it) }
                Log.d("Photo", "Primeros bytes: $firstBytes")
            }

            // Notificar galería (EXACTAMENTE COMO LA REFERENCIA)
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(imageFile)
            context.sendBroadcast(mediaScanIntent)

            statusText.text = "Guardado en álbum UXIA"

            // Mostrar toast
            handler.post {
                Toast.makeText(
                    context,
                    "Imagen guardada en álbum UXIA",
                    Toast.LENGTH_LONG
                ).show()
            }

            // Llamar callback
            callback.onReceivedImage(imageFile)

            // Cerrar después de 3 segundos
            handler.postDelayed({
                dismiss()
            }, 3000)

        } catch (e: Exception) {
            Log.e("Photo", "Error guardando foto: ${e.message}")
            statusText.text = "Error guardando foto"

            handler.post {
                Toast.makeText(
                    context,
                    "Error guardando imagen: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun resetPhotoTransfer() {
        isReceiving = false
        totalSize = 0
        bytesReceived = 0
        receivedData.reset()
        packetCount = 0
        handler.removeCallbacks(receiveTimeoutRunnable)
    }

    override fun dismiss() {
        disconnect()
        super.dismiss()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        handler.removeCallbacks(receiveTimeoutRunnable)
        super.onDetachedFromWindow()
    }
}