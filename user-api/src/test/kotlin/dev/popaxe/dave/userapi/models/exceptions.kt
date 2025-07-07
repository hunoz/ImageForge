package dev.popaxe.dave.userapi.models

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ServerInitializationExceptionTest {
    @Test
    fun `test exception message is correct`() {
        val e = ServerInitializationException("ERROR")
        Assertions.assertEquals("Error starting server: ERROR", e.message)
    }
}
