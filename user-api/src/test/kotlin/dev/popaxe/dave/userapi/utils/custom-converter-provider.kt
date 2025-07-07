package dev.popaxe.dave.userapi.utils

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.WorkspaceType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType

class CustomConverterProviderTest {
    private val provider = CustomConverterProvider()

    @Test
    fun `test converters are registered and can be retrieved by type`() {
        Assertions.assertNotNull(provider.converterFor(EnhancedType.of(WorkspaceType::class.java)))
        Assertions.assertNotNull(
            provider.converterFor(EnhancedType.of(CpuArchitecture::class.java))
        )
    }
}
