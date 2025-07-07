package dev.popaxe.dave.userapi.operations

import dev.popaxe.dave.userapi.auth.TokenHandler
import dev.popaxe.dave.userapi.models.auth.UserInfo
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import org.apache.logging.log4j.ThreadContext

abstract class BaseOperation {
    protected fun preRun(requestId: String) {
        ThreadContext.put("requestId", requestId)
    }

    protected fun postRun() {
        ThreadContext.clearMap()
    }
}

abstract class AuthBasedOperation protected constructor(private val tokenHandler: TokenHandler) :
    BaseOperation() {
    private val log: KLogger = Logging.logger(this::class)

    fun authenticate(header: String): UserInfo {
        val token = getHeaderToken(header)
        log.info { "Authenticating token for operation ${javaClass.simpleName}" }
        val user = tokenHandler.handle(token)
        log.info { "Authenticated user: ${user.username}" }
        return user
    }

    private fun getHeaderToken(header: String): String {
        if (header.startsWith("Bearer ")) {
            return header.split(" ")[1]
        }

        return header
    }

    protected inline fun <T> withAuthentication(token: String, block: (user: UserInfo) -> T): T {
        val user = authenticate(token)
        return block(user)
    }
}
