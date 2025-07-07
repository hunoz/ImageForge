package dev.popaxe.dave.userapi.auth

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import dev.popaxe.dave.generated.model.UnauthorizedException
import dev.popaxe.dave.userapi.models.auth.UserInfo
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import java.text.ParseException

class TokenHandler(
    private val appConfig: AppConfig,
    private val jwtProcessor: ConfigurableJWTProcessor<SecurityContext>,
) {
    private val log: KLogger = Logging.logger(this::class)

    fun handle(token: String?): UserInfo {
        // Process the token
        try {
            val claimsSet = jwtProcessor.process(token, null)
            val username = claimsSet.getStringClaim(appConfig.auth.usernameClaim)
            val email = claimsSet.getStringClaim(appConfig.auth.emailClaim)
            val emailVerified = claimsSet.getBooleanClaim(appConfig.auth.emailVerifiedClaim)
            val name = claimsSet.getStringClaim(appConfig.auth.nameClaim)
            return UserInfo(username, email, emailVerified, name)
        } catch (e: BadJOSEException) {
            log.error(e) { "Error parsing token" }
            throw UnauthorizedException.builder().message("Unauthorized").build()
        } catch (e: JOSEException) {
            log.error(e) { "Error parsing token" }
            throw UnauthorizedException.builder().message("Unauthorized").build()
        } catch (e: ParseException) {
            log.error(e) { "Error parsing token" }
            throw UnauthorizedException.builder().message("Unauthorized").build()
        }
    }
}
