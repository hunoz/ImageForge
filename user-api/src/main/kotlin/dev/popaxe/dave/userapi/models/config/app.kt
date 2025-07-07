package dev.popaxe.dave.userapi.models.config

import dev.popaxe.dave.userapi.utils.Constants
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL

data class AuthConfig(
    var domain: @NotBlank @URL(protocol = "https") String,
    val audience: @NotBlank String,
    val clientId: @NotBlank String,
    val redirectUri: @NotBlank String,
    val scopes: @Size(min = 1, max = 10) MutableSet<String>,
    val usernameClaim: @NotBlank String = Constants.DEFAULT_USERNAME_CLAIM,
    val emailClaim: @NotBlank String = Constants.DEFAULT_EMAIL_CLAIM,
    val emailVerifiedClaim: @NotBlank String = Constants.DEFAULT_EMAIL_VERIFIED_CLAIM,
    val nameClaim: @NotBlank String = Constants.DEFAULT_NAME_CLAIM,
)

data class AppConfig(val auth: @Valid AuthConfig)
