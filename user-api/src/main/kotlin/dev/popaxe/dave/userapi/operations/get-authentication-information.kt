package dev.popaxe.dave.userapi.operations

import dev.popaxe.dave.generated.model.GetAuthenticationInformationInput
import dev.popaxe.dave.generated.model.GetAuthenticationInformationOutput
import dev.popaxe.dave.generated.service.GetAuthenticationInformationOperation
import dev.popaxe.dave.userapi.models.config.AppConfig
import jakarta.inject.Inject
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import software.amazon.smithy.java.server.RequestContext

class GetAuthenticationInformation @Inject constructor(private val appConfig: AppConfig) :
    BaseOperation(), GetAuthenticationInformationOperation {
    override fun getAuthenticationInformation(
        input: GetAuthenticationInformationInput,
        context: RequestContext,
    ): GetAuthenticationInformationOutput {
        preRun(context.requestId)
        var domain: String = appConfig.auth.domain
        val clientId: String = appConfig.auth.clientId
        val audience: String = appConfig.auth.audience
        val redirectUri: String = appConfig.auth.redirectUri
        val scopes: Set<String> = appConfig.auth.scopes

        val scope = java.lang.String.join(" ", scopes)
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length - 1)
        }

        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val authorizeUrl =
            String.format(
                "%s/authorize?audience=%s&response_type=code&client_id=%s&redirect_uri=%s&scope=%s&code_challenge=%s&code_challenge_method=S256",
                domain,
                URLEncoder.encode(audience, Charset.defaultCharset()),
                clientId,
                URLEncoder.encode(redirectUri, Charset.defaultCharset()),
                URLEncoder.encode(scope, Charset.defaultCharset()),
                codeChallenge,
            )
        val tokenUrl = String.format("%s/oauth/token", domain)

        postRun()
        return GetAuthenticationInformationOutput.builder()
            .authorizeUrl(authorizeUrl)
            .tokenUrl(tokenUrl)
            .clientId(appConfig.auth.clientId)
            .verifier(codeVerifier)
            .build()
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val codeVerifierBytes = ByteArray(32)
        secureRandom.nextBytes(codeVerifierBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray()
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
