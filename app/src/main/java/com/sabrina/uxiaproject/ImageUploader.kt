package com.sabrina.uxiaproject.api

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImageUploader(private val context: Context) {

    interface UploadCallback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // tiempo para establecer la conexion con el servidor
        .writeTimeout(30, TimeUnit.SECONDS) // tiempo maximo para enviar la imagen al servidor
        .readTimeout(100, TimeUnit.SECONDS) // tiempo de espera de la respuesta del servidor
        .build()

    fun uploadImage(imageFile: File, callback: UploadCallback) {
        try {
            val imageBytes = imageFile.readBytes()
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // Crear array d'imatges (el servidor espera un array)
            val imagesArray = JSONArray()
            imagesArray.put(base64Image)

            // Crear JSON només amb les imatges
            val jsonObject = JSONObject().apply {
                put("imatges", imagesArray)  // El servidor ja no necessita prompt
            }

            Log.d("Upload", "Enviant JSON: $jsonObject")

            val request = Request.Builder()
                .url("https://uxia3.ieti.site/api/analitzar-imatge")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Upload", "Error: ${e.message}")
                    callback.onError(e.message ?: "Error de connexió")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        Log.d("Upload", "Èxit: $responseBody")
                        callback.onSuccess(responseBody)
                    } else {
                        Log.e("Upload", "Error ${response.code}: $responseBody")
                        callback.onError("Error ${response.code}: $responseBody")
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("Upload", "Error preparant imatge: ${e.message}")
            callback.onError(e.message ?: "Error desconegut")
        }
    }
}