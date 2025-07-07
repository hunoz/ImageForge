package dev.popaxe.dave.userapi.operations

import dev.popaxe.dave.generated.model.UnauthorizedException
import dev.popaxe.dave.userapi.auth.TokenHandler
import dev.popaxe.dave.userapi.models.auth.UserInfo
import org.apache.logging.log4j.ThreadContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

class BaseOperationTest : BaseOperation() {

    @Test
    fun `test that request id in thread context is present and correct, as well as removed after operation is done`() {
        Assertions.assertNull(ThreadContext.get("requestId"))

        preRun("thisRequestId")
        Assertions.assertEquals("thisRequestId", ThreadContext.get("requestId"))

        preRun("anotherRequestId")
        Assertions.assertEquals("anotherRequestId", ThreadContext.get("requestId"))

        postRun()
        Assertions.assertNull(ThreadContext.get("requestId"))
    }
}

class AuthBasedOperationTest {
    private lateinit var handler: TokenHandler
    private lateinit var operation: AuthBasedOperation

    @BeforeEach
    fun setUp() {
        handler = Mockito.mock(TokenHandler::class.java)
        operation = object : AuthBasedOperation(handler) {}
    }

    @Test
    fun `test authentication succeeds`() {
        Mockito.`when`(handler.handle(ArgumentMatchers.eq("token")))
            .thenReturn(UserInfo("username", "email", true, "name"))

        var user = operation.authenticate("token")
        Assertions.assertEquals("username", user.username)
        Assertions.assertEquals("email", user.email)
        Assertions.assertTrue(user.emailVerified)
        Assertions.assertEquals("name", user.name)

        Mockito.`when`(handler.handle(ArgumentMatchers.eq("Bearer token")))
            .thenReturn(UserInfo("username", "email", true, "name"))
        user = operation.authenticate("Bearer token")
        Assertions.assertEquals("username", user.username)
        Assertions.assertEquals("email", user.email)
        Assertions.assertTrue(user.emailVerified)
        Assertions.assertEquals("name", user.name)
    }

    @Test
    fun `test authentication fails and throws exception`() {
        Mockito.`when`(handler.handle(ArgumentMatchers.eq("token")))
            .thenThrow(UnauthorizedException.builder().message("Unauthorized").build())

        Assertions.assertEquals(
            "Unauthorized",
            Assertions.assertThrows(UnauthorizedException::class.java) {
                    operation.authenticate("token")
                }
                .message,
        )
    }
}
