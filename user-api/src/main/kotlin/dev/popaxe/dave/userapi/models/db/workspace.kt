package dev.popaxe.dave.userapi.models.db

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.userapi.utils.CustomConverterProvider
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey
import java.util.*

@DynamoDbBean(
    converterProviders = [CustomConverterProvider::class, DefaultAttributeConverterProvider::class]
)
class Workspace() {
    companion object {
        const val TABLE_NAME: String = "workspaces"
        val TABLE_SCHEMA: TableSchema<Workspace> = TableSchema.fromBean(Workspace::class.java)
    }

    constructor(
        id: UUID = UUID.randomUUID(),
        name: String,
        username: String,
        cloudIdentifier: String,
        workspaceType: WorkspaceType,
        cpuArchitecture: CpuArchitecture = CpuArchitecture.ARM64,
        description: String? = null,
        languageRuntimes: MutableList<LanguageRuntime> = mutableListOf(),
        packagesToInstall: MutableList<String> = mutableListOf(),
    ) : this() {
        this.id = id
        this.name = name
        this.username = username
        this.cloudIdentifier = cloudIdentifier
        this.workspaceType = workspaceType
        this.cpuArchitecture = cpuArchitecture
        this.description = description
        this.languageRuntimes = languageRuntimes
        this.packagesToInstall = packagesToInstall
    }

    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("id")
    @get:DynamoDbSecondaryPartitionKey(indexNames = ["id-username-index"])
    lateinit var id: UUID

    @get:DynamoDbAttribute("name")
    @get:DynamoDbSecondaryPartitionKey(indexNames = ["name-username-index"])
    lateinit var name: String

    @get:DynamoDbAttribute("username")
    @get:DynamoDbSecondaryPartitionKey(indexNames = ["username-index"])
    @get:DynamoDbSecondarySortKey(indexNames = ["name-username-index", "id-username-index"])
    lateinit var username: String

    @get:DynamoDbAttribute("cloudIdentifier") lateinit var cloudIdentifier: String

    @get:DynamoDbAttribute("workspaceType") lateinit var workspaceType: WorkspaceType

    @get:DynamoDbAttribute("cpuArchitecture")
    var cpuArchitecture: CpuArchitecture = CpuArchitecture.ARM64

    @get:DynamoDbAttribute("description") var description: String? = null

    @get:DynamoDbAttribute("languageRuntimes")
    var languageRuntimes: MutableList<LanguageRuntime> = mutableListOf()

    @get:DynamoDbAttribute("packagesToInstall")
    var packagesToInstall: MutableList<String> = mutableListOf()
}
