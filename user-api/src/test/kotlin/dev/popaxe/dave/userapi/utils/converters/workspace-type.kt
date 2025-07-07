package dev.popaxe.dave.userapi.utils.converters

import dev.popaxe.dave.generated.model.WorkspaceType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class WorkspaceTypeConverterTest {
    private val converter = WorkspaceTypeConverter()

    @Test
    fun `test transform from behaves as expected`() {
        var value = converter.transformFrom(WorkspaceType.MICRO)
        Assertions.assertEquals("micro", value!!.s())

        value = converter.transformFrom(WorkspaceType.STANDARD)
        Assertions.assertEquals("standard", value!!.s())

        Assertions.assertNull(converter.transformFrom(null))
    }

    @Test
    fun `test transform to behaves as expected`() {
        var value = converter.transformTo(AttributeValue.fromS("micro"))
        Assertions.assertEquals(WorkspaceType.MICRO, value)

        value = converter.transformTo(AttributeValue.fromS("standard"))
        Assertions.assertEquals(WorkspaceType.STANDARD, value)

        value = converter.transformTo(AttributeValue.fromS(null))
        Assertions.assertNull(value)

        Assertions.assertNull(converter.transformTo(null))

        Assertions.assertThrows(IllegalArgumentException::class.java) {
            converter.transformTo(AttributeValue.fromS("value"))
        }
    }

    @Test
    fun `test type is correct`() {
        Assertions.assertEquals(WorkspaceType::class.java, converter.type().rawClass())
    }

    @Test
    fun `test attribute value type is correct`() {
        Assertions.assertEquals(AttributeValueType.S, converter.attributeValueType())
    }
}
