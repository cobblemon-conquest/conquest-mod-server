package dev.albercl.conquestmod.config

data class Config(
    val endpoint: String = "http://localhost:3000/members/account-link/finish",
    val timeoutSeconds: Long = 5
)