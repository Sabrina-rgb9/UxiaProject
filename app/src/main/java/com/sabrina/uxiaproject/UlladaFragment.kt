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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.sabrina.uxiaproject.ui.BLEconnDialog // Tu diálogo REAL
import java.io.File

class UlladaFragment : Fragment(), BLEconnDialog.BLEConnectionCallback {

    private lateinit var btnReceiveImage: Button
    private lateinit var tvNoDeviceSelected: TextView
    private lateinit var tvStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private var bleDialog: BLEconnDialog? = null

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
            startRealBluetoothConnection()
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

    @SuppressLint("MissingPermission")
    private fun startRealBluetoothConnection() {
        val deviceAddress = sharedPreferences.getString("ESP32_DEVICE_ADDRESS", null)

        if (deviceAddress == null) {
            Toast.makeText(requireContext(), "Selecciona un dispositiu primer", Toast.LENGTH_SHORT).show()
            return
        }

        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions()
            return
        }

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(requireContext(), "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

        // Usar tu BLEconnDialog REAL
        bleDialog = BLEconnDialog(requireContext(), device, this)
        bleDialog?.apply {
            setCancelable(false)
            show()
        }

        tvStatus.text = "⏳ Connectant amb ESP32..."
    }

    // CALLBACKS del BLEconnDialog REAL
    override fun onConnectionSuccess(gatt: BluetoothGatt) {
        requireActivity().runOnUiThread {
            tvStatus.text = "✅ Connectat! Rebotant imatge..."
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
            Toast.makeText(requireContext(), "Imatge rebuda: ${file.name}", Toast.LENGTH_SHORT).show()

            // Aquí guardarías la imagen en la galería
            saveImageToGallery(file)
        }
    }

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

        requestPermissions(permissions, 101)
    }

    private fun saveImageToGallery(file: File) {
        // TODO: Implementar guardado en galería en álbum "UXIA"
        Toast.makeText(requireContext(), "Guardant imatge a la galeria...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        bleDialog?.dismiss()
    }
}