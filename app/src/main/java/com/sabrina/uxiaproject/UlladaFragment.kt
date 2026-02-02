package com.sabrina.uxiaproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sabrina.uxiaproject.ui.BLEconnDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

import android.content.ContentValues


class UlladaFragment : Fragment(), BLEconnDialog.BLEConnectionCallback {

    private lateinit var btnReceiveImage: Button
    private lateinit var tvNoDeviceSelected: TextView
    private lateinit var tvStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var bleDialog: BLEconnDialog? = null

    // Para permisos de almacenamiento
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothConnection()
        } else {
            tvStatus.text = "❌ Permisos denegats"
            Toast.makeText(
                requireContext(),
                "Necessitis permisos per guardar imatges",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Para permisos de Bluetooth
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkStoragePermissions()
        } else {
            tvStatus.text = "❌ Permisos Bluetooth denegats"
            Toast.makeText(
                requireContext(),
                "Necessitis permisos Bluetooth",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ullada, container, false)

        btnReceiveImage = view.findViewById(R.id.btn_receive_image)
        tvNoDeviceSelected = view.findViewById(R.id.tv_no_device_selected)
        tvStatus = view.findViewById(R.id.tv_status)

        sharedPreferences = requireContext().getSharedPreferences("UXIA_PREFS", Context.MODE_PRIVATE)

        btnReceiveImage.setOnClickListener {
            connectAndReceiveImage()
        }

        tvNoDeviceSelected.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AjustosFragment())
                .addToBackStack("ullada_to_ajustos")
                .commit()
        }

        checkBluetoothDevice()

        return view
    }

    override fun onResume() {
        super.onResume()
        checkBluetoothDevice()
    }

    private fun checkBluetoothDevice() {
        val deviceAddress = sharedPreferences.getString("ESP32_DEVICE_ADDRESS", null)
        val deviceName = sharedPreferences.getString("ESP32_DEVICE_NAME", null)

        if (deviceAddress != null && deviceName != null) {
            btnReceiveImage.isEnabled = true
            btnReceiveImage.alpha = 1f
            tvNoDeviceSelected.visibility = View.GONE
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "✅ Preparat: $deviceName"
        } else {
            btnReceiveImage.isEnabled = false
            btnReceiveImage.alpha = 0.5f
            tvNoDeviceSelected.visibility = View.VISIBLE
            tvStatus.visibility = View.GONE
        }
    }

    private fun connectAndReceiveImage() {
        val deviceAddress = sharedPreferences.getString("ESP32_DEVICE_ADDRESS", null)

        if (deviceAddress == null) {
            Toast.makeText(requireContext(), "Selecciona un dispositiu primer", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Verificar permisos Bluetooth
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        // 2. Verificar Bluetooth activado
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(requireContext(), "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
            val enableBtIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
            return
        }

        // 3. Verificar permisos de almacenamiento
        checkStoragePermissions()
    }

    private fun startBluetoothConnection() {
        val deviceAddress = sharedPreferences.getString("ESP32_DEVICE_ADDRESS", null) ?: return

        try {
            val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter

            @SuppressLint("MissingPermission")
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

            // Mostrar diálogo BLEconnDialog
            bleDialog = BLEconnDialog(requireContext(), device, this)
            bleDialog?.show()

            tvStatus.text = "⏳ Connectant amb ESP32..."

        } catch (e: SecurityException) {
            tvStatus.text = "❌ Error de permisos"
            Toast.makeText(requireContext(), "Error de permisos: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            tvStatus.text = "❌ Error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // CALLBACKS de BLEconnDialog
    override fun onConnectionSuccess(gatt: BluetoothGatt) {
        requireActivity().runOnUiThread {
            tvStatus.text = "✅ Connectat! Prem 'Sol·licitar Imatge'"
            Toast.makeText(requireContext(), "Connectat amb èxit!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(error: String) {
        requireActivity().runOnUiThread {
            tvStatus.text = "❌ Error: $error"
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnectionCancelled() {
        requireActivity().runOnUiThread {
            tvStatus.text = "⏹️ Connexió cancel·lada"
            Toast.makeText(requireContext(), "Connexió cancel·lada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onReceivedImage(file: File) {
        requireActivity().runOnUiThread {
            tvStatus.text = "✅ Imatge rebuda!"
            Toast.makeText(
                requireContext(),
                "Imatge rebuda: ${file.name}",
                Toast.LENGTH_LONG
            ).show()

            // Mover a galería
            moveToGallery(file)
        }
    }

    private fun moveToGallery(sourceFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Para Android 10+
                saveImageToGalleryAndroid10Plus(sourceFile)
            } else {
                // Para Android 9 y anteriores
                saveImageToGalleryLegacy(sourceFile)
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error guardant imatge: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveImageToGalleryLegacy(sourceFile: File) {
        val albumDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "UXIA"
        )

        if (!albumDir.exists()) {
            albumDir.mkdirs()
        }

        val destFile = File(albumDir, sourceFile.name)

        if (sourceFile.renameTo(destFile)) {
            // Notificar a la galería
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = Uri.fromFile(destFile)
            requireContext().sendBroadcast(mediaScanIntent)

            Toast.makeText(requireContext(), "Imatge guardada a UXIA/", Toast.LENGTH_LONG).show()
        } else {
            // Si rename falla, copiar
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(requireContext(), "Imatge copiada a UXIA/", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveImageToGalleryAndroid10Plus(sourceFile: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/UXIA")
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Toast.makeText(requireContext(), "Imatge guardada a la galeria", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        bluetoothPermissionLauncher.launch(permissions)
    }

    private fun checkStoragePermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startBluetoothConnection()
            return true
        } else {
            storagePermissionLauncher.launch(permissions)
            return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleDialog?.dismiss()
    }
}