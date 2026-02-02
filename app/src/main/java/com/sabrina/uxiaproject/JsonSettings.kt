package com.sabrina.uxiaproject

import android.content.Context
import org.json.JSONObject
import java.io.*

class JsonSettings(private val context: Context) {

    private val settingsFile = File(context.filesDir, "settings.json")

    // Guardar dispositivo seleccionado
    fun saveSelectedDevice(name: String, address: String) {
        try {
            val json = JSONObject().apply {
                put("selected_device", JSONObject().apply {
                    put("name", name)
                    put("address", address)
                })
            }

            FileOutputStream(settingsFile).use { fos ->
                fos.write(json.toString().toByteArray())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Obtener dispositivo seleccionado
    fun getSelectedDevice(): Pair<String, String>? {
        if (!settingsFile.exists()) return null

        return try {
            val jsonString = FileInputStream(settingsFile).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val device = json.getJSONObject("selected_device")

            val name = device.getString("name")
            val address = device.getString("address")

            Pair(name, address)
        } catch (e: Exception) {
            null
        }
    }

    // Eliminar dispositivo seleccionado
    fun clearSelectedDevice() {
        if (settingsFile.exists()) {
            settingsFile.delete()
        }
    }

    // Verificar si hay dispositivo seleccionado
    fun hasSelectedDevice(): Boolean {
        return settingsFile.exists() && getSelectedDevice() != null
    }
}