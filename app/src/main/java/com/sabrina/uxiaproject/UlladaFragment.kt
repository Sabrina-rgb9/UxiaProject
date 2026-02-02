package com.sabrina.uxiaproject

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class UlladaFragment : Fragment(), BLEconnDialog.BLEConnectionCallback {

    private lateinit var btnReceiveImage: Button
    private lateinit var tvNoDeviceSelected: TextView
    private lateinit var tvStatus: TextView
    private lateinit var jsonSettings: JsonSettings
    private var bleDialog: BLEconnDialog? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothConnection()
        } else {
            tvStatus.text = "Permisos denegados"
            Toast.makeText(requireContext(), "Permisos necesarios", Toast.LENGTH_LONG).show()
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

        jsonSettings = JsonSettings(requireContext())

        btnReceiveImage.setOnClickListener {
            onReceiveImageClicked()
        }

        tvNoDeviceSelected.setOnClickListener {
            navigateToAjustos()
        }

        updateButtonState()

        return view
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    private fun updateButtonState() {
        // Cambiado: usar JsonSettings
        val device = jsonSettings.getSelectedDevice()

        if (device != null) {
            val (deviceName, deviceAddress) = device
            btnReceiveImage.isEnabled = true
            btnReceiveImage.alpha = 1.0f
            tvNoDeviceSelected.visibility = View.GONE
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Dispositivo: $deviceName"
        } else {
            btnReceiveImage.isEnabled = false
            btnReceiveImage.alpha = 0.5f
            tvNoDeviceSelected.visibility = View.VISIBLE
            tvStatus.visibility = View.GONE
        }
    }

    private fun onReceiveImageClicked() {
        val device = jsonSettings.getSelectedDevice()

        if (device == null) {
            Toast.makeText(requireContext(), "Selecciona un dispositivo primero", Toast.LENGTH_SHORT).show()
            navigateToAjustos()
            return
        }

        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        if (!checkStoragePermissions()) {
            requestStoragePermissions()
            return
        }

        startBluetoothConnection()
    }

    private fun navigateToAjustos() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AjustosFragment())
            .addToBackStack("ullada_to_ajustos")
            .commit()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothConnection() {
        // Cambiado: usar JsonSettings
        val device = jsonSettings.getSelectedDevice() ?: return
        val (_, deviceAddress) = device

        try {
            val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

            bleDialog = BLEconnDialog(requireContext(), device, this)
            bleDialog?.show()

            tvStatus.text = "Conectando con ESP32..."

        } catch (e: Exception) {
            tvStatus.text = "Error: ${e.message}"
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // CALLBACKS del BLEconnDialog - ¡SOLO NOTIFICAR, NO GUARDAR DE NUEVO!
    override fun onConnectionSuccess(gatt: BluetoothGatt) {
        requireActivity().runOnUiThread {
            tvStatus.text = "¡Conectado! Esperando imagen..."
            Toast.makeText(requireContext(), "Conectado al ESP32", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionFailed(error: String) {
        requireActivity().runOnUiThread {
            tvStatus.text = "Error: $error"
            Toast.makeText(requireContext(), "Error: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onConnectionCancelled() {
        requireActivity().runOnUiThread {
            tvStatus.text = "Conexión cancelada"
            Toast.makeText(requireContext(), "Conexión cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    // ¡IMPORTANTE! Solo actualizar UI, NO guardar de nuevo
    override fun onReceivedImage(file: File) {
        requireActivity().runOnUiThread {
            tvStatus.text = "✅ Imagen guardada en álbum UXIA"

            // Solo mostrar notificación, el diálogo ya guardó la imagen
            Toast.makeText(
                requireContext(),
                "Imagen guardada: ${file.name}",
                Toast.LENGTH_LONG
            ).show()

            // NO LLAMAR A saveImageToUXIAAlbum aquí
            // El BLEconnDialog ya guardó la imagen
        }
    }

    // Verificación de permisos
    private fun checkBluetoothPermissions(): Boolean {
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

        requestPermissionLauncher.launch(permissions)
    }

    private fun checkStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 - No necesita WRITE_EXTERNAL_STORAGE para MediaStore
            true
        } else {
            // Android 9-
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10-12 no necesita WRITE_EXTERNAL_STORAGE
            arrayOf<String>()
        } else {
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleDialog?.dismiss()
    }
}