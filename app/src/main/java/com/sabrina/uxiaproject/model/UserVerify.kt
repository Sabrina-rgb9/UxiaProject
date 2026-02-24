package com.sabrina.uxiaproject.model

// model/UserVerify.kt
data class UserVerifyResponse(
    val status: String,
    val message: String,
    val data: VerifyData?
)

data class VerifyData(
    val api_key: String?  // ✅ El servidor només retorna api_key
)