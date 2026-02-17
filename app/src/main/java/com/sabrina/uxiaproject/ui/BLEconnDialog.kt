package com.sabrina.uxiaproject.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
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
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager


class BLEconnDialog(
    context: Context,
    private val device: BluetoothDevice,
    private val connectionCallback: BLEConnectionCallback
) : Dialog(context) {

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)

        fun onImageReceived(file: File, imageData: ByteArray)  // 🔴 NOU
    }

    // UUIDs
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Views
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvImage: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnConnect: Button
    private lateinit var btnCancel: Button

    // BLE
    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false
    private var isReceiving = false
    private var received = false
    private var receivedFile: File? = null

    // Timeouts
    private val CONNECTION_TIMEOUT = 15000L
    private val RECEIVE_TIMEOUT = 90000L // 90 segons
    // Variables per foto
    private val receivedData = ByteArrayOutputStream(1024 * 1024) // 1MB

    private var consecutiveTimeouts = 0
    private var totalSize = 0
    private var packetCount = 0
    private var lastPacketTime = 0L

    private var mtuConfirmed = false

    // Timeouts runnables - SENSE anotacions
// Timeouts runnables - VERSIÓ CORREGIDA
    private val connectionTimeoutRunnable = Runnable {
        // Verificar que tot és vàlid abans d'actuar
        if (isConnecting && context != null && isShowing) {
            try {
                Log.d("BLE", "Timeout de connexió executant-se")

                // Cridar disconnect (que ja té els permisos)
                disconnect()

                // Notificar al callback
                connectionCallback.onConnectionFailed("Timeout de connexió")

                // Tancar diàleg si encara està obert
                if (isShowing) {
                    dismiss()
                }
            } catch (e: SecurityException) {
                // Error de permisos
                Log.e("BLE", "Error de permisos en timeout: ${e.message}")
                connectionCallback.onConnectionFailed("Error de permisos Bluetooth")
                if (isShowing) {
                    dismiss()
                }
            } catch (e: Exception) {
                // Altres errors
                Log.e("BLE", "Error en timeout: ${e.message}")
                if (isShowing) {
                    dismiss()
                }
            }
        }
    }

    private val receiveTimeoutRunnable = Runnable {
        handler.post {
            if (isReceiving) {
                consecutiveTimeouts++
                Log.e(
                    "BLE_DEBUG",
                    "TIMEOUT #$consecutiveTimeouts! Paquets: $packetCount, Bytes: ${receivedData.size()}/$totalSize"
                )

                if (consecutiveTimeouts > 3) {
                    tvStatus.text = "Error: Massa timeouts"
                    resetPhotoTransfer()
                } else {
                    // Intentar recuperar
                    tvStatus.text = "Timeout... reintentant"
                    resetReceiveTimeout()
                }
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ble_connection)

        // Inicialitzar views
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress)
        tvStatus = findViewById(R.id.tvStatus)
        tvImage = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        btnConnect = findViewById(R.id.btnConnect)
        btnCancel = findViewById(R.id.btnCancel)

        // Configurar dades del dispositiu
        tvDeviceName.text = device.name ?: "ESP32"
        tvDeviceAddress.text = device.address
        tvImage.visibility = View.GONE

        // Configurar botons
        btnConnect.setOnClickListener {
            if (received) {
                receivedFile?.let { file ->
                    connectionCallback.onReceivedImage(file)
                }
                disconnect()
                dismiss()
            } else if (!isConnecting) {
                connectToDevice()
            }
        }

        btnCancel.setOnClickListener {
            cancelConnection()
        }

        // Connectar automàticament
        connectToDevice()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice() {
        if (isConnecting) return

        isConnecting = true
        updateUIForConnecting()

        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        try {
            bluetoothGatt = device.connectGatt(context.applicationContext, false, gattCallback)
            Log.d("BLE", "Iniciant connexió amb ${device.address}")
        } catch (e: Exception) {
            tvStatus.text = "Error de connexió"
            Toast.makeText(context, "Error al connectar: ${e.message}", Toast.LENGTH_SHORT).show()
            connectionCallback.onConnectionFailed("Error de connexió: ${e.message}")
            dismiss()
        }
    }

    private fun updateUIForConnecting() {
        tvStatus.text = "Connectant..."
        btnConnect.isEnabled = false
        btnConnect.text = "Connectant..."
        progressBar.isIndeterminate = true
    }

    private fun updateUIForConnected() {
        tvStatus.text = "Connectat, esperant imatge..."
        btnConnect.text = "Connectat"
        progressBar.isIndeterminate = false
        progressBar.progress = 0
    }

    private fun updateUIForReceived() {
        btnConnect.isEnabled = true
        btnConnect.text = "Enviar"
        tvImage.visibility = View.VISIBLE
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("MissingPermission")
    private fun disconnect() {
        try {
            handler.removeCallbacks(connectionTimeoutRunnable)
            handler.removeCallbacks(receiveTimeoutRunnable)

            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                Log.e("BLE", "Error de permisos en disconnect: ${e.message}")
            } catch (e: Exception) {
                Log.e("BLE", "Error al desconnectar: ${e.message}")
            }

            bluetoothGatt = null
            isConnecting = false
            isReceiving = false

        } catch (e: Exception) {
            Log.e("BLE", "Error fatal en disconnect: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun cancelConnection() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        disconnect()
        connectionCallback.onConnectionCancelled()
        dismiss()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false

                        updateUIForConnected()
                        Log.d("BLE", "✓ Connectat al dispositiu")

                        // 🔴 IMPORTANT: Sol·licitar MTU just després de connectar
                        try {
                            gatt.requestMtu(517)
                            Log.d("BLE_MTU", "Sol·licitant MTU 517")
                        } catch (e: Exception) {
                            Log.e("BLE_MTU", "Error sol·licitant MTU: ${e.message}")
                        }

                        // Descobrir serveis després d'un petit retard
                        handler.postDelayed({
                            try {
                                gatt.discoverServices()
                                Log.d("BLE", "Descobrint serveis...")
                            } catch (e: Exception) {
                                tvStatus.text = "Error descobrint serveis"
                                connectionCallback.onConnectionFailed("Error descobrint serveis")
                            }
                        }, 500)
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false

                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            tvStatus.text = "Desconnectat"
                            Log.d("BLE", "Desconnectat normalment")
                        } else {
                            val errorMsg = when (status) {
                                0x08 -> "Timeout"
                                0x13 -> "Terminat per host local"
                                0x16 -> "Terminat per host remot"
                                0x3E -> "No connectat"
                                else -> "Error: 0x${status.toString(16)}"
                            }
                            tvStatus.text = "Error: $errorMsg"
                            Log.e("BLE", "Error de connexió: $errorMsg")
                            connectionCallback.onConnectionFailed(errorMsg)
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE_MTU", "MTU canviat a: $mtu bytes")
                    tvStatus.text = "MTU: $mtu"
                    mtuConfirmed = true  // Important!
                } else {
                    Log.e("BLE_MTU", "Error canviant MTU: $status")
                    mtuConfirmed = false
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Serveis descoberts")

                // Esperar que el MTU es configuri abans de continuar
                handler.postDelayed({
                    setupNotifications(gatt)
                }, 1000) // Esperar 1 segon
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun setupNotifications(gatt: BluetoothGatt) {
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                Log.d("BLE", "✓ Servei ESP32 trobat")
                val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                if (characteristic != null) {
                    Log.d("BLE", "✓ Característica trobada")

                    // Habilitar notificacions
                    gatt.setCharacteristicNotification(characteristic, true)

                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        handler.post {
                            tvStatus.text = "Llest per rebre imatges! (MTU: ${if(mtuConfirmed) "OK" else "No configurat"})"
                            connectionCallback.onConnectionSuccess(gatt)
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "✓ Descriptor configurat correctament")
                } else {
                    Log.e("BLE", "Error configurant descriptor: $status")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val data = characteristic.value
                if (data.isNotEmpty()) {
                    handleIncomingData(data)
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "✓ Escriptura completada")
                } else {
                    Log.e("BLE", "Error en escriptura: $status")
                }
            }
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        packetCount++
        lastPacketTime = System.currentTimeMillis()


        // 🔴 NOU: Verificar si el paquet és massa gran
        if (data.size > 253) {
            Log.w("BLE_MTU", "Paquet de ${data.size} bytes > MTU 253! Possible problema")
        }

        Log.d("BLE_BYTES", "Paquet $packetCount rebut: ${data.size} bytes")

        // Verificar si és paquet de finalització
        if (data.size == 4 && data.contentEquals(
                byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
            )
        ) {
            Log.d("BLE_BYTES", "🏁 Paquet de finalització rebut!")
            if (isReceiving && receivedData.size() > 0) {
                completePhotoTransfer()
            }
            return
        }


        // Primer paquet (mida)
        if (!isReceiving && data.size == 4) {
            try {
                totalSize = (data[0].toInt() and 0xFF) +
                        ((data[1].toInt() and 0xFF) shl 8) +
                        ((data[2].toInt() and 0xFF) shl 16) +
                        ((data[3].toInt() and 0xFF) shl 24)

                Log.d("BLE_BYTES", "Mida anunciada: $totalSize bytes")
                isReceiving = true
                receivedData.reset()

                handler.post {
                    tvStatus.text = "Rebent foto ($totalSize bytes)..."
                    progressBar.max = totalSize
                    progressBar.progress = 0
                }

                startReceiveTimeout()
                return

            } catch (e: Exception) {
                Log.e("BLE", "Error interpretant mida: ${e.message}")
            }
        }

        // 🔴 IMPORTANT: Acceptar qualsevol mida de paquet (fins a 512 bytes)
        if (isReceiving) {
            val previousSize = receivedData.size()
            receivedData.write(data)  // No hi ha límit!
            val currentSize = receivedData.size()
            if (currentSize > 500000) { // 500KB
                Log.w("BLE_MEM", "Fitxer gran: $currentSize bytes")
            }
            // Log per veure si rebem paquets grans
            if (data.size > 100) {
                Log.d("BLE_BYTES", "📦 Paquet GRAN: ${data.size} bytes")
            }

            handler.post {
                progressBar.progress = currentSize

                if (packetCount % 5 == 0 || currentSize == totalSize) {
                    val percent = if (totalSize > 0) (currentSize * 100) / totalSize else 0
                    tvStatus.text = "Rebent: $currentSize/$totalSize bytes ($percent%) - Paquet $packetCount"
                    Log.d("BLE_BYTES", "📊 Progrés: $currentSize/$totalSize bytes ($percent%) - Paquet $packetCount")
                }
            }

            resetReceiveTimeout()

            if (totalSize > 0 && currentSize >= totalSize) {
                Log.d("BLE_BYTES", "Mida assolida! Completant...")
                completePhotoTransfer()
            }
        }
    }

    private fun startReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        // Timeout progressiu: més temps per imatges més grans
        val timeout = if (totalSize > 50000) 90000L else 60000L
        handler.postDelayed(receiveTimeoutRunnable, timeout)
    }

    private fun resetReceiveTimeout() {
        handler.removeCallbacks(receiveTimeoutRunnable)
        handler.postDelayed(receiveTimeoutRunnable, RECEIVE_TIMEOUT)
    }

    private fun completePhotoTransfer() {
        handler.post {
            tvStatus.text = "Processant imatge..."
            progressBar.isIndeterminate = true
        }

        Thread {
            try {
                val finalSize = receivedData.size()
                val dataStr = receivedData.toString().trim()
                Log.d("BLE", "📦 Dades rebudes: $finalSize bytes")

                val decodedData = try {
                    Base64.decode(dataStr, Base64.DEFAULT)
                } catch (e: Exception) {
                    Log.e("BLE", "Error Base64: ${e.message}")
                    try {
                        Base64.decode(dataStr, Base64.NO_WRAP)
                    } catch (e2: Exception) {
                        Log.e("BLE", "Error Base64 NO_WRAP: ${e2.message}")
                        receivedData.toByteArray()
                    }
                }

                Log.d("BLE", "Dades decodificades: ${decodedData.size} bytes")
                savePhoto(decodedData)

            } catch (e: Exception) {
                Log.e("BLE", "Error fatal: ${e.message}")
                handler.post {
                    tvStatus.text = "Error processant imatge"
                }
            }
        }.start()
    }

    private fun savePhoto(imageData: ByteArray) {
        try {
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
            receivedFile = imageFile

            // Mostrar imatge al dialog
            handler.post {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                if (bitmap != null) {
                    tvImage.setImageBitmap(bitmap)
                    tvImage.visibility = View.VISIBLE
                    tvStatus.text = "Imatge rebuda correctament"
                    progressBar.visibility = View.GONE
                    received = true
                    updateUIForReceived()
                    btnConnect.setOnClickListener {
                        if (received) {
                            handleSendButtonClick(imageData)
                        }
                    }
                }
            }

            // Notificar galeria
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(imageFile)
            context.sendBroadcast(mediaScanIntent)

            resetPhotoTransfer()

        } catch (e: Exception) {
            Log.e("Photo", "Error guardant foto: ${e.message}")
            handler.post {
                tvStatus.text = "Error guardant foto"
            }
        }
    }
    private fun handleSendButtonClick(imageData: ByteArray) {
        try {
            // 1. Primer notificar al callback
            receivedFile?.let { file ->
                connectionCallback.onImageReceived(file, imageData)
            }

            // 2. Desconnectar BLE
            safeDisconnect()

            // 3. Tancar diàleg
            safeDismiss()

        } catch (e: Exception) {
            Log.e("BLE", "Error enviant imatge: ${e.message}")
            safeDismiss()
        }
    }

    private fun safeDisconnect() {
        try {
            handler.removeCallbacks(connectionTimeoutRunnable)
            handler.removeCallbacks(receiveTimeoutRunnable)

            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                Log.e("BLE", "Error de permisos: ${e.message}")
            } catch (e: Exception) {
                Log.e("BLE", "Error desconnectant: ${e.message}")
            }

            bluetoothGatt = null
            isConnecting = false
            isReceiving = false

        } catch (e: Exception) {
            Log.e("BLE", "Error fatal en disconnect: ${e.message}")
        }
    }

    private fun safeDismiss() {
        try {
            if (isShowing) {
                super.dismiss()
            }
        } catch (e: Exception) {
            Log.e("BLE", "Error en dismiss: ${e.message}")
        }
    }

    private fun resetPhotoTransfer() {
        isReceiving = false
        totalSize = 0
        receivedData.reset()
        packetCount = 0
        handler.removeCallbacks(receiveTimeoutRunnable)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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