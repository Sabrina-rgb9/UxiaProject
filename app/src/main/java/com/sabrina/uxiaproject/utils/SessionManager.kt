package com.sabrina.uxiaproject.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_API_TOKEN = "api_token"
        const val KEY_IS_VERIFIED = "is_verified"
        const val KEY_PHONE = "phone"
        const val KEY_IS_REGISTERED = "is_registered"
    }

    fun saveApiToken(token: String) {
        prefs.edit().putString(KEY_API_TOKEN, token).apply()
    }

    fun getApiToken(): String? {
        return prefs.getString(KEY_API_TOKEN, null)
    }

    fun isVerified(): Boolean {
        return prefs.getBoolean(KEY_IS_VERIFIED, false)
    }

    fun setVerified(verified: Boolean) {
        prefs.edit().putBoolean(KEY_IS_VERIFIED, verified).apply()
    }

    fun savePhone(phone: String) {
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getPhone(): String? {
        return prefs.getString(KEY_PHONE, null)
    }

    fun setRegistered(registered: Boolean) {
        prefs.edit().putBoolean(KEY_IS_REGISTERED, registered).apply()
    }

    fun isRegistered(): Boolean {
        return prefs.getBoolean(KEY_IS_REGISTERED, false)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean {
        return getApiToken() != null && isVerified()
    }
}