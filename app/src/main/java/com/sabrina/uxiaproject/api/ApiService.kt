package com.sabrina.uxiaproject.api

import android.content.Context
import android.util.Log
import com.sabrina.uxiaproject.model.UserRegister
import com.sabrina.uxiaproject.model.UserRegisterResponse
import com.sabrina.uxiaproject.model.UserVerifyResponse
import com.sabrina.uxiaproject.model.VerifyData
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiService(private val context: Context) {

    // Interfícies de callback
    interface RegisterCallback {
        fun onSuccess(response: UserRegisterResponse)
        fun onError(error: String)
    }

    interface VerifyCallback {
        fun onSuccess(response: UserVerifyResponse)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://uxia3.ieti.site/api"

    /**
     * Registre d'usuari
     * Endpoint: /usuaris/registrar
     */
    fun registerUser(user: UserRegister, callback: RegisterCallback) {
        try {
            val jsonObject = JSONObject().apply {
                put("nickname", user.nickname)
                put("email", user.email)
                put("telefon", user.telefon)
            }

            Log.d("ApiService", "📤 Enviant registre: $jsonObject")

            val request = Request.Builder()
                .url("$baseUrl/usuaris/registrar")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("ApiService", "❌ Error de connexió: ${e.message}")
                    callback.onError(e.message ?: "Error de connexió")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    try {
                        Log.d("ApiService", "📥 Resposta registre: $responseBody")

                        if (response.isSuccessful) {
                            val jsonResponse = JSONObject(responseBody)
                            val status = jsonResponse.optString("status", "ERROR")
                            val message = jsonResponse.optString("message", "")

                            val userData = if (jsonResponse.has("data")) {
                                val data = jsonResponse.getJSONObject("data")
                                com.sabrina.uxiaproject.model.UserData(
                                    id = data.optInt("id"),
                                    nickname = data.optString("nickname"),
                                    email = data.optString("email"),
                                    telefon = data.optString("telefon"),
                                    createdAt = data.optString("createdAt")
                                )
                            } else null

                            val registerResponse = UserRegisterResponse(
                                status = status,
                                message = message,
                                data = userData
                            )

                            callback.onSuccess(registerResponse)

                        } else {
                            val errorMsg = try {
                                val jsonError = JSONObject(responseBody)
                                jsonError.optString("message", "Error ${response.code}")
                            } catch (e: Exception) {
                                "Error ${response.code}: $responseBody"
                            }
                            callback.onError(errorMsg)
                        }

                    } catch (e: Exception) {
                        Log.e("ApiService", "❌ Error parsejant resposta: ${e.message}")
                        callback.onError("Error processant resposta del servidor")
                    }
                }
            })

        } catch (e: Exception) {
            Log.e("ApiService", "❌ Error preparant petició: ${e.message}")
            callback.onError(e.message ?: "Error desconegut")
        }
    }

    /**
     * Verificació del codi SMS
     * Endpoint: /usuaris/validar
     * El servidor espera: telefon i codi_validacio
     */
    fun verifyUser(telefon: String, codiValidacio: String, callback: VerifyCallback) {
        try {
            val jsonObject = JSONObject().apply {
                put("telefon", telefon)
                put("codi_validacio", codiValidacio)
            }

            // 🔴 LOG ABANS D'ENVIAR
            Log.d("API_DEBUG", "🔵 Enviant petició a: $baseUrl/usuaris/validar")
            Log.d("API_DEBUG", "🔵 Body: $jsonObject")

            val request = Request.Builder()
                .url("$baseUrl/usuaris/validar")
                .post(jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("API_DEBUG", "❌ Failure: ${e.message}")
                    callback.onError(e.message ?: "Error de connexió")
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    try {
                        Log.d("ApiService", "📥 Resposta verificació: $responseBody")

                        if (response.isSuccessful) {
                            val jsonResponse = JSONObject(responseBody)
                            val status = jsonResponse.optString("status", "ERROR")
                            val message = jsonResponse.optString("message", "")

                            // ✅ El servidor retorna api_key dins de data
                            val apiKey = if (jsonResponse.has("data")) {
                                val data = jsonResponse.getJSONObject("data")
                                data.optString("api_key", null)
                            } else null

                            val verifyResponse = UserVerifyResponse(
                                status = status,
                                message = message,
                                data = if (apiKey != null) VerifyData(api_key = apiKey) else null
                            )

                            callback.onSuccess(verifyResponse)

                        } else {
                            // ... codi d'error ...
                        }
                    } catch (e: Exception) {
                        Log.e("ApiService", "❌ Error parsejant resposta: ${e.message}")
                        callback.onError("Error processant resposta del servidor")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("API_DEBUG", "❌ Exception: ${e.message}")
            callback.onError(e.message ?: "Error desconegut")
        }
    }
}