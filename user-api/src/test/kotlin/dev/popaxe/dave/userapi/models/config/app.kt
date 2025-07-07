package dev.popaxe.dave.userapi.models.config

import dev.popaxe.dave.userapi.utils.Constants.DEFAULT_EMAIL_CLAIM
import dev.popaxe.dave.userapi.utils.Constants.DEFAULT_EMAIL_VERIFIED_CLAIM
import dev.popaxe.dave.userapi.utils.Constants.DEFAULT_NAME_CLAIM
import dev.popaxe.dave.userapi.utils.Constants.DEFAULT_USERNAME_CLAIM
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AuthConfigTest {

    @Test
    fun `test default values are as expected`() {
        val config =
            AuthConfig(
                "domain",
                "aud",
                "id",
                "http://localhost/callback",
                mutableSetOf("openid", "profile", "email"),
            )
        assertEquals(DEFAULT_USERNAME_CLAIM, config.usernameClaim)
        assertEquals(DEFAULT_EMAIL_CLAIM, config.emailClaim)
        assertEquals(DEFAULT_EMAIL_VERIFIED_CLAIM, config.emailVerifiedClaim)
        assertEquals(DEFAULT_NAME_CLAIM, config.nameClaim)
    }

    @Test
    fun `test overridden values are as expected`() {
        val config =
            AuthConfig(
                "domain",
                "aud",
                "id",
                "http://localhost/callback",
                mutableSetOf("openid", "profile", "email"),
                "fake_username",
                "fake_email",
                "fake_email_verified",
                "fake_name",
            )

        assertEquals("fake_username", config.usernameClaim)
        assertEquals("fake_email", config.emailClaim)
        assertEquals("fake_email_verified", config.emailVerifiedClaim)
        assertEquals("fake_name", config.nameClaim)
    }
}
