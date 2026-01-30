package com.sabrina.uxiaproject

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class AjustosFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var tvSelectedDevice: TextView
    private lateinit var btnSelectDevice: Button
    private lateinit var btnClearDevice: Button

    private val REQUEST_CODE_BLUETOOTH = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ajustos, container, false)

        tvSelectedDevice = view.findViewById(R.id.tv_selected_device)
        btnSelectDevice = view.findViewById(R.id.btn_select_device)
        btnClearDevice = view.findViewById(R.id.btn_clear_device)

        sharedPreferences = requireContext().getSharedPreferences("UXIA_PREFS", Context.MODE_PRIVATE)

        btnSelectDevice.setOnClickListener {
            openBluetoothSelection()
        }

        btnClearDevice.setOnClickListener {
            clearSelectedDevice()
        }

        updateDeviceInfo()

        return view
    }

    private fun openBluetoothSelection() {
        // ESTO ES REAL - busca dispositivos de verdad
        val intent = Intent(requireContext(), BluetoothSelectionActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_BLUETOOTH)
    }

    private fun updateDeviceInfo() {
        val deviceAddress = sharedPreferences.getString("ESP32_DEVICE_ADDRESS", null)
        val deviceName = sharedPreferences.getString("ESP32_DEVICE_NAME", null)

        if (deviceAddress != null && deviceName != null) {
            tvSelectedDevice.text = "✅ Dispositiu seleccionat:\nNom: $deviceName\nAdreça: $deviceAddress"
            btnClearDevice.visibility = View.VISIBLE
        } else {
            tvSelectedDevice.text = "⚠️ Cap dispositiu ESP32 seleccionat"
            btnClearDevice.visibility = View.GONE
        }
    }

    private fun clearSelectedDevice() {
        sharedPreferences.edit()
            .remove("ESP32_DEVICE_NAME")
            .remove("ESP32_DEVICE_ADDRESS")
            .apply()

        updateDeviceInfo()
        Toast.makeText(requireContext(), "Dispositiu esborrat", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_BLUETOOTH && resultCode == Activity.RESULT_OK) {
            val deviceName = data?.getStringExtra("DEVICE_NAME")
            val deviceAddress = data?.getStringExtra("DEVICE_ADDRESS")

            if (deviceName != null && deviceAddress != null) {
                sharedPreferences.edit()
                    .putString("ESP32_DEVICE_NAME", deviceName)
                    .putString("ESP32_DEVICE_ADDRESS", deviceAddress)
                    .apply()

                updateDeviceInfo()
                Toast.makeText(requireContext(), "Dispositiu guardat: $deviceName", Toast.LENGTH_SHORT).show()

                // Volver a Ullada automáticamente
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }
}