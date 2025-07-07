package dev.popaxe.dave.userapi.dagger.modules

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import dagger.Module
import dagger.Provides
import dev.popaxe.dave.userapi.auth.TokenHandler
import dev.popaxe.dave.userapi.models.ServerInitializationException
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.models.config.AuthConfig
import dev.popaxe.dave.userapi.utils.Constants
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.ClasspathLoader
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validation
import jakarta.validation.ValidatorFactory
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Module(includes = [AwsModule::class])
class AppModule {
    private val log: KLogger = Logging.logger(this::class)

    companion object {
        private const val APP_CONFIG_PATH: String = Constants.EnvironmentVariables.APP_CONFIG_PATH
        private val factory: ValidatorFactory = Validation.buildDefaultValidatorFactory()
        private val validator = factory.validator
    }

    @Provides
    @Singleton
    fun gson(): Gson {
        return GsonBuilder().serializeNulls().create()
    }

    @Provides
    @Singleton
    fun appConfig(gson: Gson): AppConfig {
        val configPath: String = System.getenv(APP_CONFIG_PATH)
        if (configPath.isBlank()) {
            throw ServerInitializationException("APP_CONFIG_PATH environment variable must be set")
        }

        if (!configPath.endsWith(".json")) {
            throw ServerInitializationException("APP_CONFIG_PATH must be a JSON file")
        }

        if (!Paths.get(configPath).toFile().exists()) {
            throw ServerInitializationException("APP_CONFIG_PATH file does not exist")
        }

        val path: Path = Paths.get(configPath)

        try {
            val content: String = Files.readString(path)
            val root: JsonElement = gson.fromJson(content, JsonElement::class.java)
            val auth: JsonElement = root.asJsonObject.get("auth")
            val authConfig: AuthConfig = gson.fromJson(auth, AuthConfig::class.java)
            val config: AppConfig = AppConfig(authConfig)
            try {
                val errors: Set<ConstraintViolation<AppConfig>> = validator.validate(config)
                if (errors.isNotEmpty()) {
                    val err: ConstraintViolation<AppConfig> = errors.iterator().next()
                    val property: String = err.propertyPath.toString()
                    val error: String = err.message
                    throw RuntimeException("Field $property failed validation: $error")
                }
            } catch (e: Exception) {
                throw ServerInitializationException("APP_CONFIG_PATH file is invalid: ${e.message}")
            }
            return config
        } catch (e: ServerInitializationException) {
            throw e
        } catch (e: Exception) {
            throw ServerInitializationException("Error reading APP_CONFIG_PATH file: ${e.message}")
        }
    }

    @Provides
    @Singleton
    fun jwtProcessor(appConfig: AppConfig): ConfigurableJWTProcessor<SecurityContext> {
        val jwksUrl: URL
        val domain: String = appConfig.auth.domain
        try {
            jwksUrl = URL("https://$domain/.well-known/jwks.json")
        } catch (e: MalformedURLException) {
            log.error(e) { "Error parsing JWKS URL" }
            throw ServerInitializationException("Error parsing JWKS URL: ${e.message}")
        }

        val resourceRetriever: DefaultResourceRetriever = DefaultResourceRetriever(2000, 2000)
        val keySource: JWKSource<SecurityContext> =
            JWKSourceBuilder.create<SecurityContext>(jwksUrl, resourceRetriever).build()

        val jwtProcessor: ConfigurableJWTProcessor<SecurityContext> = DefaultJWTProcessor()
        val keySelector: JWSKeySelector<SecurityContext> =
            JWSVerificationKeySelector(JWSAlgorithm.RS256, keySource)
        jwtProcessor.setJWSKeySelector(keySelector)

        jwtProcessor.setJWTClaimsSetVerifier(
            DefaultJWTClaimsVerifier(
                JWTClaimsSet.Builder().issuer("https://$domain/").build(),
                setOf(appConfig.auth.audience),
            )
        )

        return jwtProcessor
    }

    @Provides
    @Singleton
    fun tokenHandler(
        appConfig: AppConfig,
        jwtProcessor: ConfigurableJWTProcessor<SecurityContext>,
    ): TokenHandler {
        return TokenHandler(appConfig, jwtProcessor)
    }

    @Provides
    @Singleton
    fun pebbleEngine(): PebbleEngine {
        return PebbleEngine.Builder().loader(ClasspathLoader()).build()
    }
}
