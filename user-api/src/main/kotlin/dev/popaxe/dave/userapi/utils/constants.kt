package dev.popaxe.dave.userapi.utils

object Constants {
    const val DEFAULT_USERNAME_CLAIM: String = "preferred_username"
    const val DEFAULT_EMAIL_CLAIM: String = "email"
    const val DEFAULT_EMAIL_VERIFIED_CLAIM: String = "email_verified"
    const val DEFAULT_NAME_CLAIM: String = "name"

    object EnvironmentVariables {
        const val APP_CONFIG_PATH: String = "APP_CONFIG_PATH"
    }
}
