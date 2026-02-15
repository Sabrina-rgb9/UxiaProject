package com.sabrina.uxiaproject.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.sabrina.uxiaproject.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
        fun onProgressUpdate(current: Int, total: Int)
    }

    companion object {
        const val ESP32_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
        const val ESP32_CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"
    }

    // Views - Declaradas como lateinit pero todas se inicializarán en onCreate
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var deviceNameText: TextView
    private lateinit var deviceAddressText: TextView
    private lateinit var bytesText: TextView
    private lateinit var previewImage: ImageView
    private lateinit var btnCancel: Button
    private lateinit var btnConnect: Button
    private lateinit var iconBluetooth: ImageView

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var isConnecting = false
    private val CONNECTION_TIMEOUT = 15000L

    // Variables para recepción de imagen
    private val receivedData = ByteArrayOutputStream()
    private var totalSize = 0
    private var isReceiving = false
    private var bytesReceived = 0
    private val RECEIVE_TIMEOUT = 30000L
    private var packetCount = 0
    private var lastPacketTime = 0L

    // Timeouts
    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            updateStatus("Timeout de conexión", true)
            disconnect()
            callback.onConnectionFailed("Timeout de conexión")
            dismiss()
        }
    }

    private val receiveTimeoutRunnable = Runnable {
        if (isReceiving) {
            updateStatus("Timeout en recepción", true)
            Log.e("BLE", "Timeout en recepción de imagen")
            resetPhotoTransfer()
            Toast.makeText(context, "Timeout recibiendo imagen", Toast.LENGTH_SHORT).show()
        }
    }

    init {
        setTitle("Conectant amb ESP32")
        setCancelable(true)
        setOnCancelListener {
            disconnect()
            callback.onConnectionCancelled()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_ble_connection, null)
        setView(view)

        // INICIALIZAR TODAS LAS VISTAS AQUÍ
        initializeViews(view)

        // Configurar valores iniciales
        setupInitialUI()

        // Configurar botones
        setupButtons()

        // Iniciar conexión
        connectToDevice()
    }

    private fun initializeViews(view: View) {
        // Inicializar TODAS las vistas lateinit
        deviceNameText = view.findViewById(R.id.deviceNameText)
        deviceAddressText = view.findViewById(R.id.deviceAddressText)
        progressBar = view.findViewById(R.id.progressBar)
        statusText = view.findViewById(R.id.statusText)
        bytesText = view.findViewById(R.id.bytesText)
        previewImage = view.findViewById(R.id.previewImage)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnConnect = view.findViewById(R.id.btnConnect)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupInitialUI() {
        // Configurar valores iniciales
        deviceNameText.text = device.name ?: "ESP32"
        deviceAddressText.text = device.address
        previewImage.visibility = View.GONE
        bytesText.visibility = View.GONE
        progressBar.isIndeterminate = true
        statusText.text = "Iniciant connexió..."
    }

    private fun setupButtons() {
        btnCancel.setOnClickListener {
            disconnect()
            callback.onConnectionCancelled()
            dismiss()
        }

        btnConnect.text = "Connectant..."
        btnConnect.isEnabled = false
    }

    private fun updateStatus(message: String, showIndeterminate: Boolean = false) {
        handler.post {
            statusText.text = message
            if (showIndeterminate) {
                progressBar.isIndeterminate = true
            }
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        handler.post {
            progressBar.isIndeterminate = false
            progressBar.max = total
            progressBar.progress = current
            val percent = if (total > 0) (current * 100) / total else 0
            statusText.text = "Rebent: $percent%"
            bytesText.visibility = View.VISIBLE
            bytesText.text = "$current/$total bytes"
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        if (isConnecting) return

        isConnecting = true
        updateStatus("Connectant...", true)

        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        try {
            bluetoothGatt = device.connectGatt(context.applicationContext, false, gattCallback)
            Log.d("BLE", "Iniciant connexió amb ${device.address}")
        } catch (e: Exception) {
            updateStatus("Error de connexió: ${e.message}", false)
            Toast.makeText(context, "Error al connectar: ${e.message}", Toast.LENGTH_SHORT).show()
            callback.onConnectionFailed("Error de connexió: ${e.message}")
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
            Log.e("BLE", "Error al desconnectar: ${e.message}")
        }

        bluetoothGatt = null
        isConnected = false
        isConnecting = false
        isReceiving = false

        Log.d("BLE", "Desconnectat")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        isConnected = true

                        updateStatus("Connectat! Cercant serveis...", true)

                        try {
                            gatt.requestMtu(517)
                        } catch (e: Exception) {
                            Log.e("BLE", "Error sol·licitant MTU")
                        }

                        handler.postDelayed({
                            try {
                                gatt.discoverServices()
                                Log.d("BLE", "Descobrint serveis...")
                            } catch (e: Exception) {
                                updateStatus("Error descobrint serveis", false)
                                callback.onConnectionFailed("Error descobrint serveis")
                            }
                        }, 500)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        isConnected = false

                        val errorMsg = if (status == BluetoothGatt.GATT_SUCCESS) {
                            "Desconnectat"
                        } else {
                            "Error de connexió: 0x${status.toString(16)}"
                        }
                        updateStatus(errorMsg, false)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "MTU canviat a: $mtu bytes")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    updateStatus("Serveis trobats", true)

                    try {
                        val service = gatt.getService(UUID.fromString(ESP32_SERVICE_UUID))
                        if (service != null) {
                            val characteristic = service.getCharacteristic(UUID.fromString(ESP32_CHARACTERISTIC_UUID))
                            if (characteristic != null) {
                                gatt.setCharacteristicNotification(characteristic, true)

                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
                                )
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)

                                    handler.post {
                                        statusText.text = "Esperant imatge..."
                                        progressBar.isIndeterminate = false
                                        progressBar.progress = 0
                                        btnConnect.text = "Connectat"
                                    }

                                    callback.onConnectionSuccess(gatt)
                                } else {
                                    updateStatus("Error: Descriptor no trobat", false)
                                    callback.onConnectionFailed("Descriptor no trobat")
                                }
                            } else {
                                updateStatus("Error: Característica no trobada", false)
                                callback.onConnectionFailed("Característica no trobada")
                            }
                        } else {
                            updateStatus("Error: Servei ESP32 no trobat", false)
                            callback.onConnectionFailed("Servei ESP32 no trobat")
                        }
                    } catch (e: Exception) {
                        updateStatus("Error de configuració: ${e.message}", false)
                        callback.onConnectionFailed("Error de configuració: ${e.message}")
                    }
                } else {
                    updateStatus("Error descobrint serveis: $status", false)
                    callback.onConnectionFailed("Error descobrint serveis: $status")
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
                    handleIncomingData(data)
                }
            }
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        packetCount++
        lastPacketTime = System.currentTimeMillis()

        Log.d("BLE", "Paquet $packetCount rebut: ${data.size} bytes")

        // Verificar paquete de finalización
        if (data.size == 4 && data.contentEquals(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            )
        ) {
            if (isReceiving && receivedData.size() > 0) {
                completePhotoTransfer()
            }
            return
        }

        // Primer paquete (tamaño)
        if (!isReceiving && data.size == 4) {
            try {
                totalSize = (data[0].toInt() and 0xFF) +
                        ((data[1].toInt() and 0xFF) shl 8) +
                        ((data[2].toInt() and 0xFF) shl 16) +
                        ((data[3].toInt() and 0xFF) shl 24)

                isReceiving = true
                receivedData.reset()
                bytesReceived = 0

                handler.post {
                    statusText.text = "Rebent imatge..."
                    progressBar.max = totalSize
                    progressBar.progress = 0
                    progressBar.isIndeterminate = false
                    bytesText.visibility = View.VISIBLE
                    bytesText.text = "0/$totalSize bytes"
                }

                Log.d("BLE", "Mida anunciada: $totalSize bytes")
                startReceiveTimeout()

            } catch (e: Exception) {
                Log.e("BLE", "Error interpretant mida: ${e.message}")
            }
            return
        }

        // Recibiendo datos
        if (isReceiving) {
            receivedData.write(data)
            bytesReceived += data.size

            val currentSize = receivedData.size()

            // Actualizar progreso
            updateProgress(currentSize, totalSize)

            // Enviar progreso al callback
            callback.onProgressUpdate(currentSize, totalSize)

            // Reiniciar timeout
            resetReceiveTimeout()

            // Completar si llegamos al tamaño esperado
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

    private fun completePhotoTransfer() {
        handler.post {
            statusText.text = "Processant imatge..."
            progressBar.isIndeterminate = true
            bytesText.visibility = View.GONE
        }

        // Procesar en segundo plano
        Thread {
            try {
                val dataStr = receivedData.toString().trim()
                val decodedData = try {
                    Base64.decode(dataStr, Base64.DEFAULT)
                } catch (e: Exception) {
                    Log.e("BLE", "Error en Base64, usant dades raw: ${e.message}")
                    receivedData.toByteArray()
                }

                saveAndDisplayPhoto(decodedData)
            } catch (e: Exception) {
                handler.post {
                    statusText.text = "Error processant imatge"
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveAndDisplayPhoto(imageData: ByteArray) {
        try {
            // Mostrar imagen en preview
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

            handler.post {
                if (bitmap != null) {
                    previewImage.setImageBitmap(bitmap)
                    previewImage.visibility = View.VISIBLE
                    statusText.text = "Imatge rebuda correctament"
                    progressBar.visibility = View.GONE
                } else {
                    statusText.text = "Error: No s'ha pogut decodificar la imatge"
                }
            }

            // Guardar en álbum
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "UXIA_${timestamp}.jpg"

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

            // Notificar galería
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(imageFile)
            context.sendBroadcast(mediaScanIntent)

            handler.post {
                statusText.text = "Guardat a l'àlbum UXIA"
                btnConnect.text = "Tancar"
                btnConnect.isEnabled = true
                btnConnect.setOnClickListener {
                    dismiss()
                }
            }

            callback.onReceivedImage(imageFile)

        } catch (e: Exception) {
            Log.e("Photo", "Error guardant foto: ${e.message}")
            handler.post {
                statusText.text = "Error guardant foto"
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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