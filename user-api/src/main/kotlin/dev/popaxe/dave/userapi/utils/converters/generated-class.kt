package dev.popaxe.dave.userapi.utils.converters

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceType
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

class CpuArchitectureConverter : AttributeConverter<CpuArchitecture> {
    override fun transformFrom(cpuArchitecture: CpuArchitecture?): AttributeValue? {
        if (cpuArchitecture == null) return null
        return AttributeValue.fromS(cpuArchitecture.value())
    }

    override fun transformTo(attributeValue: AttributeValue?): CpuArchitecture? {
        if (attributeValue?.s() == null) return null
        return CpuArchitecture.from(attributeValue.s())
    }

    override fun type(): EnhancedType<CpuArchitecture> {
        return EnhancedType.of(CpuArchitecture::class.java)
    }

    override fun attributeValueType(): AttributeValueType {
        return AttributeValueType.S
    }
}

class WorkspaceTypeConverter : AttributeConverter<WorkspaceType> {
    override fun transformFrom(workspaceType: WorkspaceType?): AttributeValue? {
        if (workspaceType == null) return null
        return AttributeValue.fromS(workspaceType.value())
    }

    override fun transformTo(attributeValue: AttributeValue?): WorkspaceType? {
        if (attributeValue?.s() == null) return null
        return WorkspaceType.from(attributeValue.s())
    }

    override fun type(): EnhancedType<WorkspaceType> {
        return EnhancedType.of(WorkspaceType::class.java)
    }

    override fun attributeValueType(): AttributeValueType {
        return AttributeValueType.S
    }
}

class LanguageRuntimesConverter : AttributeConverter<List<LanguageRuntime>> {
    override fun transformFrom(runtimes: List<LanguageRuntime>?): AttributeValue? {
        if (runtimes == null) return null
        return AttributeValue.fromL(runtimes.map { AttributeValue.fromS(it.value()) })
    }

    override fun transformTo(attributeValue: AttributeValue?): List<LanguageRuntime>? {
        if (attributeValue?.hasL() == false || attributeValue?.l() == null) return null
        return attributeValue.l().map { LanguageRuntime.from(it.s()) }
    }

    override fun type(): EnhancedType<List<LanguageRuntime>> {
        return EnhancedType.listOf(LanguageRuntime::class.java)
    }

    override fun attributeValueType(): AttributeValueType {
        return AttributeValueType.L
    }
}
