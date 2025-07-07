package dev.popaxe.dave.userapi.testutils

import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.auth.UserInfo
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.models.config.AuthConfig
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.IOException
import java.io.Writer
import java.util.*
import org.mockito.Mockito.mock
import software.amazon.smithy.java.server.RequestContext

object CommonUtilities {

    fun createAppConfig(): AppConfig {
        return AppConfig(
            AuthConfig(
                "domain",
                "aud",
                "id",
                "http://localhost/callback",
                mutableSetOf("openid", "profile", "email"),
                emailClaim = "email",
                usernameClaim = "username",
                nameClaim = "name",
                emailVerifiedClaim = "email_verified",
            )
        )
    }

    fun createUser(): UserInfo {
        return UserInfo("username", "email", true, "name")
    }

    fun createAppComponent(): AppComponent {
        return mock(AppComponent::class.java)
    }

    class FakeRequestContext(private var requestId: String = "requestId") : RequestContext {

        override fun getRequestId(): String {
            return requestId
        }

        fun setRequestId(id: String) {
            requestId = id
        }
    }

    class FakePebbleTemplate : PebbleTemplate {
        @Throws(IOException::class) override fun evaluate(writer: Writer) {}

        @Throws(IOException::class) override fun evaluate(writer: Writer, locale: Locale) {}

        @Throws(IOException::class)
        override fun evaluate(writer: Writer, context: Map<String, Any>) {}

        @Throws(IOException::class)
        override fun evaluate(writer: Writer, context: Map<String, Any>, locale: Locale) {}

        @Throws(IOException::class) override fun evaluateBlock(blockName: String, writer: Writer) {}

        @Throws(IOException::class)
        override fun evaluateBlock(blockName: String, writer: Writer, locale: Locale) {}

        @Throws(IOException::class)
        override fun evaluateBlock(blockName: String, writer: Writer, context: Map<String, Any>) {}

        @Throws(IOException::class)
        override fun evaluateBlock(
            blockName: String,
            writer: Writer,
            context: Map<String, Any>,
            locale: Locale,
        ) {}

        override fun getName(): String {
            return "name"
        }
    }
}
