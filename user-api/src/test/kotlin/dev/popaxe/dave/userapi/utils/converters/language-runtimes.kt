package dev.popaxe.dave.userapi.utils.converters

import dev.popaxe.dave.generated.model.LanguageRuntime
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class LanguageRuntimesConverterTest {
    private val converter = LanguageRuntimesConverter()

    @Test
    fun `test transform from behaves as expected`() {
        var value = converter.transformFrom(listOf(LanguageRuntime.JAVA11))
        Assertions.assertTrue { value?.hasL() == true && value?.l()?.size == 1 }
        Assertions.assertEquals("java@11", value?.l()[0]?.s())

        value = converter.transformFrom(listOf(LanguageRuntime.PYTHON3_6))
        Assertions.assertTrue { value?.hasL() == true && value.l()?.size == 1 }
        Assertions.assertEquals("python@3.6", value?.l()[0]?.s())

        Assertions.assertNull(converter.transformFrom(null))
    }

    @Test
    fun `test transform to behaves as expected`() {
        var value = converter.transformTo(AttributeValue.fromL(listOf()))
        Assertions.assertNotNull(value)
        Assertions.assertTrue { value?.isEmpty() == true }

        value = converter.transformTo(AttributeValue.fromL(listOf(AttributeValue.fromS("java@11"))))
        Assertions.assertTrue { value?.isNotEmpty() == true && value?.size == 1 }
        Assertions.assertEquals(LanguageRuntime.JAVA11, value?.get(0))

        value = converter.transformTo(AttributeValue.fromS(""))
        Assertions.assertNull(value)

        Assertions.assertNull(converter.transformTo(null))

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            converter.transformTo(AttributeValue.fromL(listOf(AttributeValue.fromS("random"))))
        }
    }

    @Test
    fun `test type is correct`() {
        Assertions.assertEquals(List::class.java, converter.type().rawClass())
    }

    @Test
    fun `test attribute value type is correct`() {
        Assertions.assertEquals(AttributeValueType.L, converter.attributeValueType())
    }
}
