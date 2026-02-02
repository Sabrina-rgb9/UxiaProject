package com.sabrina.uxiaproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sabrina.uxiaproject.adapter.BluetoothDeviceAdapter
import com.sabrina.uxiaproject.model.BluetoothDevice

class BluetoothSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val REQUEST_ENABLE_BT = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_selection)

        // Inicializar vistas
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        // Comprobar permisos y cargar dispositivos
        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Permisos básicos Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        // Permiso de localización necesario para Bluetooth en Android 6+
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                100
            )
        } else {
            checkBluetoothAndLoadDevices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkBluetoothAndLoadDevices()
            } else {
                Toast.makeText(this, "Permisos necessaris per Bluetooth", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun checkBluetoothAndLoadDevices() {
        if (bluetoothAdapter == null) {
            tvStatus.text = "❌ Bluetooth no suportat"
            Toast.makeText(this, "Aquest dispositiu no suporta Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            loadPairedDevices()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                loadPairedDevices()
            } else {
                Toast.makeText(this, "Bluetooth requereix estar activat", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        tvStatus.text = "🔍 Cercant dispositius aparellats..."
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        // Obtener dispositivos apareados
        val pairedDevices = bluetoothAdapter!!.bondedDevices
        val deviceList = mutableListOf<BluetoothDevice>()

        // Filtrar solo dispositivos ESP32 (opcional)
        pairedDevices.forEach { device ->
            val deviceName = device.name ?: "Dispositiu desconegut"
            val deviceAddress = device.address

            // Puedes filtrar por nombre si sabes cómo se llaman tus ESP32
            // if (deviceName.contains("ESP32", ignoreCase = true)) {
            deviceList.add(BluetoothDevice(deviceName, deviceAddress))
            // }
        }

        // Actualizar UI
        runOnUiThread {
            progressBar.visibility = View.GONE

            if (deviceList.isEmpty()) {
                tvStatus.text = "📭 Cap dispositiu aparellat trobat"
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "No hi ha dispositius Bluetooth aparellats\n\nConnecta el teu ESP32 primer des dels Ajusts del sistema"
            } else {
                tvStatus.text = "✅ ${deviceList.size} dispositius trobats"

                // Configurar adaptador
                recyclerView.adapter = BluetoothDeviceAdapter(deviceList) { device ->
                    selectDevice(device)
                }
            }
        }
    }

    private fun selectDevice(device: BluetoothDevice) {
        // Devolver dispositivo seleccionado
        val resultIntent = Intent().apply {
            putExtra("DEVICE_NAME", device.name)
            putExtra("DEVICE_ADDRESS", device.address)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}