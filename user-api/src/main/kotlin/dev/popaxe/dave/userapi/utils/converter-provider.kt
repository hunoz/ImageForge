package dev.popaxe.dave.userapi.utils

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.userapi.utils.converters.CpuArchitectureConverter
import dev.popaxe.dave.userapi.utils.converters.LanguageRuntimesConverter
import dev.popaxe.dave.userapi.utils.converters.WorkspaceTypeConverter
import io.github.oshai.kotlinlogging.KLogger
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverterProvider
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.utils.ImmutableMap

class CustomConverterProvider : AttributeConverterProvider {
    private val log: KLogger = Logging.logger(this::class)

    companion object {
        private val defaultConverterProvider: DefaultAttributeConverterProvider =
            DefaultAttributeConverterProvider()
        private val converterCache: Map<EnhancedType<*>, AttributeConverter<*>> =
            ImmutableMap.of<EnhancedType<*>, AttributeConverter<*>>(
                EnhancedType.of(WorkspaceType::class.java),
                WorkspaceTypeConverter(),
                EnhancedType.of(CpuArchitecture::class.java),
                CpuArchitectureConverter(),
                EnhancedType.listOf(LanguageRuntime::class.java),
                LanguageRuntimesConverter(),
            )
    }

    override fun <T> converterFor(enhancedType: EnhancedType<T>): AttributeConverter<T> {
        log.info { "Getting converter for type ${enhancedType.rawClass()}" }
        if (converterCache[enhancedType] == null) {
            return defaultConverterProvider.converterFor(enhancedType)
        }

        return converterCache[enhancedType] as AttributeConverter<T>
    }
}
