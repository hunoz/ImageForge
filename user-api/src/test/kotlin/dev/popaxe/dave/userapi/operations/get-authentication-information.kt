package dev.popaxe.dave.userapi.operations

import dev.popaxe.dave.generated.model.GetAuthenticationInformationInput
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.testutils.CommonUtilities
import dev.popaxe.dave.userapi.testutils.CommonUtilities.createAppConfig
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetAuthenticationInformationTest {
    private lateinit var appConfig: AppConfig
    private lateinit var operation: GetAuthenticationInformation

    @BeforeEach
    fun setup() {
        appConfig = createAppConfig()
        operation = GetAuthenticationInformation(appConfig)
    }

    @Test
    fun `test getting authentication information succeeds and response is as expected`() {
        var response =
            operation.getAuthenticationInformation(
                GetAuthenticationInformationInput.builder().build(),
                CommonUtilities.FakeRequestContext(),
            )

        Assertions.assertEquals(appConfig.auth.clientId, response.clientId())
        Assertions.assertTrue(response.authorizeUrl().contains(appConfig.auth.domain))

        appConfig.auth.domain = "domain.com/"
        response =
            operation.getAuthenticationInformation(
                GetAuthenticationInformationInput.builder().build(),
                CommonUtilities.FakeRequestContext(),
            )
        Assertions.assertEquals(appConfig.auth.clientId, response.clientId())
        Assertions.assertTrue(response.authorizeUrl().contains(appConfig.auth.domain))
    }
}
