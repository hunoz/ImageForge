package dev.popaxe.dave.userapi.models.auth

data class UserInfo(
    val username: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String,
)
