package com.sabrina.uxiaproject.model

data class UserRegister(
    val nickname: String,
    val email: String,
    val telefon: String
)

data class UserRegisterResponse(
    val status: String,
    val message: String,
    val data: UserData?
)

data class UserData(
    val id: Int,
    val nickname: String,
    val email: String,
    val telefon: String,
    val createdAt: String
)