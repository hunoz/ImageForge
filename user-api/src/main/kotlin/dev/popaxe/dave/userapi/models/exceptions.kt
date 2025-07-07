package dev.popaxe.dave.userapi.models

class ServerInitializationException(message: String) :
    RuntimeException("Error starting server: $message")
