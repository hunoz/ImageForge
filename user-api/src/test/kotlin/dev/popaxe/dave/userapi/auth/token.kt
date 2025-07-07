package dev.popaxe.dave.userapi.auth

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import dev.popaxe.dave.generated.model.UnauthorizedException
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.testutils.CommonUtilities.createAppConfig
import java.text.ParseException
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class TokenHandlerTest {
    private lateinit var appConfig: AppConfig
    private lateinit var jwtProcessor: ConfigurableJWTProcessor<SecurityContext>
    private lateinit var tokenHandler: TokenHandler

    @BeforeEach
    fun setUp() {
        appConfig = createAppConfig()
        jwtProcessor =
            Mockito.mock(ConfigurableJWTProcessor::class.java)
                as ConfigurableJWTProcessor<SecurityContext>
        tokenHandler = TokenHandler(appConfig, jwtProcessor)
    }

    @Test
    fun `token is valid and parsed correctly`() {
        val token = "token"
        val claims: Map<String, Any> =
            mapOf(
                JWTClaimNames.AUDIENCE to "audience",
                JWTClaimNames.JWT_ID to "id",
                JWTClaimNames.ISSUER to "issuer",
                JWTClaimNames.ISSUED_AT to Instant.now().epochSecond,
                JWTClaimNames.EXPIRATION_TIME to Instant.now().plus(5, ChronoUnit.DAYS).epochSecond,
                JWTClaimNames.NOT_BEFORE to Instant.now().minus(1, ChronoUnit.DAYS).epochSecond,
                JWTClaimNames.SUBJECT to "subject",
                "username" to "thisismyusername",
                "email" to "thisismyemail",
                "email_verified" to true,
                "name" to "thisismyname",
            )

        Assertions.assertDoesNotThrow {
            val claimsSet = JWTClaimsSet.parse(claims)
            Mockito.`when`(
                    jwtProcessor.process(
                        ArgumentMatchers.eq(token),
                        ArgumentMatchers.eq<SecurityContext?>(null),
                    )
                )
                .thenReturn(claimsSet)
            val userInfo = tokenHandler.handle(token)

            Assertions.assertEquals("thisismyusername", userInfo.username)
            Assertions.assertEquals("thisismyemail", userInfo.email)
            Assertions.assertTrue(userInfo.emailVerified)
            Assertions.assertEquals("thisismyname", userInfo.name)
        }
    }

    @Test
    fun `token is invalid and throws exception`() {
        val token = "token"
        Assertions.assertDoesNotThrow {
            Mockito.`when`(
                    jwtProcessor.process(
                        ArgumentMatchers.eq(token),
                        ArgumentMatchers.eq<SecurityContext?>(null),
                    )
                )
                .thenThrow(BadJOSEException("Invalid token"))
        }

        Assertions.assertEquals(
            "Unauthorized",
            Assertions.assertThrows(UnauthorizedException::class.java) {
                    tokenHandler.handle(token)
                }
                .message,
        )
    }

    @Test
    fun `token not parseable and throws exception`() {
        val token = "token"
        Assertions.assertDoesNotThrow {
            Mockito.`when`(
                    jwtProcessor.process(
                        ArgumentMatchers.eq(token),
                        ArgumentMatchers.eq<SecurityContext?>(null),
                    )
                )
                .thenThrow(ParseException("Token unparseable", 0))
        }

        Assertions.assertEquals(
            "Unauthorized",
            Assertions.assertThrows(UnauthorizedException::class.java) {
                    tokenHandler.handle(token)
                }
                .message,
        )
    }

    @Test
    fun `some other exception happens and throws exception`() {
        val token = "token"
        Assertions.assertDoesNotThrow {
            Mockito.`when`(
                    jwtProcessor.process(
                        ArgumentMatchers.eq(token),
                        ArgumentMatchers.eq<SecurityContext?>(null),
                    )
                )
                .thenThrow(JOSEException("Error"))
        }

        Assertions.assertEquals(
            "Unauthorized",
            Assertions.assertThrows(UnauthorizedException::class.java) {
                    tokenHandler.handle(token)
                }
                .message,
        )
    }
}
