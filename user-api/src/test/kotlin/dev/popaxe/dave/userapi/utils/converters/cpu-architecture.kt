package dev.popaxe.dave.userapi.utils.converters

import dev.popaxe.dave.generated.model.CpuArchitecture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class CpuArchitectureConverterTest {
    private val converter = CpuArchitectureConverter()

    @Test
    fun `test transform from behaves as expected`() {
        var value = converter.transformFrom(CpuArchitecture.X86_64)
        Assertions.assertEquals("x86_64", value!!.s())

        value = converter.transformFrom(CpuArchitecture.ARM64)
        Assertions.assertEquals("arm64", value!!.s())

        Assertions.assertNull(converter.transformFrom(null))
    }

    @Test
    fun `test transform to behaves as expected`() {
        var value = converter.transformTo(AttributeValue.fromS("x86_64"))
        Assertions.assertEquals(CpuArchitecture.X86_64, value)

        value = converter.transformTo(AttributeValue.fromS("arm64"))
        Assertions.assertEquals(CpuArchitecture.ARM64, value)

        value = converter.transformTo(AttributeValue.fromS(null))
        Assertions.assertNull(value)

        Assertions.assertNull(converter.transformTo(null))

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            converter.transformTo(AttributeValue.fromS("value"))
        }
    }

    @Test
    fun `test type is correct`() {
        Assertions.assertEquals(CpuArchitecture::class.java, converter.type().rawClass())
    }

    @Test
    fun `test attribute value type is correct`() {
        Assertions.assertEquals(AttributeValueType.S, converter.attributeValueType())
    }
}
