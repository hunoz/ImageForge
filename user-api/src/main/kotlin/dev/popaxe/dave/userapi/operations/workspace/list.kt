package dev.popaxe.dave.userapi.operations.workspace

import com.google.gson.reflect.TypeToken
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.ListWorkspacesInput
import dev.popaxe.dave.generated.model.ListWorkspacesOutput
import dev.popaxe.dave.generated.model.SortOrder
import dev.popaxe.dave.generated.service.ListWorkspacesOperation
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import jakarta.inject.Inject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.smithy.java.server.RequestContext

class ListWorkspaces @Inject constructor(component: AppComponent) :
    WorkspaceOperation(component), ListWorkspacesOperation {
    val log: KLogger = Logging.logger(this::class)

    override fun listWorkspaces(
        input: ListWorkspacesInput,
        context: RequestContext,
    ): ListWorkspacesOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                val inputToken: Map<String, AttributeValue>? =
                    input.nextToken()?.let {
                        try {
                            deserializeToken(it)
                        } catch (e: Exception) {
                            log.error(e) { "Error deserializing token" }
                            throw InternalServerErrorException.builder()
                                .message("Internal Server Error")
                                .build()
                        }
                    }

                val queryConditional =
                    QueryConditional.keyEqualTo { key: Key.Builder ->
                        key.partitionValue(user.username)
                    }

                val workspaceIterable =
                    table.index("username-index").query { query: QueryEnhancedRequest.Builder ->
                        query
                            .limit(input.pageSize())
                            .exclusiveStartKey(inputToken)
                            .scanIndexForward(input.sortOrder().equals(SortOrder.ASC))
                            .queryConditional(queryConditional)
                    }

                val dbWorkspaces: MutableList<Workspace> = mutableListOf()
                val nextToken: Map<String, AttributeValue>? =
                    with(workspaceIterable.firstOrNull()) {
                        this?.items()?.forEach { ws -> dbWorkspaces.add(ws) }

                        this?.lastEvaluatedKey()
                    }

                if (dbWorkspaces.isEmpty()) {
                    return ListWorkspacesOutput.builder()
                        .items(listOf())
                        .hasNextPage(false)
                        .nextToken(null)
                        .build()
                }

                val response: DescribeInstancesResponse

                try {
                    response =
                        ec2.describeInstances { request: DescribeInstancesRequest.Builder ->
                            request.instanceIds(
                                dbWorkspaces.map { ws -> ws.cloudIdentifier }.toList()
                            )
                        }
                } catch (e: Exception) {
                    log.error(e) { "Error fetching EC2 instances" }
                    throw InternalServerErrorException.builder()
                        .message("Internal Server Error")
                        .build()
                }
                val instanceMap =
                    response
                        .reservations()
                        .flatMap { it.instances() }
                        .associateBy { it.instanceId() }
                val workspaces =
                    dbWorkspaces.mapNotNull { dbWorkspace ->
                        val instance = instanceMap[dbWorkspace.cloudIdentifier]
                        instance?.let {
                            dev.popaxe.dave.generated.model.Workspace.builder()
                                .id(dbWorkspace.id.toString())
                                .workspaceType(dbWorkspace.workspaceType)
                                .cloudIdentifier(dbWorkspace.cloudIdentifier)
                                .cpuArchitecture(dbWorkspace.cpuArchitecture)
                                .name(dbWorkspace.name)
                                .description(dbWorkspace.description)
                                .username(dbWorkspace.username)
                                .status(getWsStatus(it.state().name()))
                                .languageRuntimes(dbWorkspace.languageRuntimes)
                                .packagesToInstall(dbWorkspace.packagesToInstall)
                                .build()
                        }
                    }

                val outputToken: String? =
                    nextToken?.let {
                        try {
                            serializeToken(it)
                        } catch (e: Exception) {
                            log.error(e) { "Error serializing token" }
                            throw InternalServerErrorException.builder()
                                .message("Internal Server Error")
                                .build()
                        }
                    }

                ListWorkspacesOutput.builder()
                    .hasNextPage(nextToken != null)
                    .items(workspaces)
                    .nextToken(outputToken)
                    .build()
            }
        }
    }

    @Throws(IOException::class)
    fun serializeToken(token: Map<String, AttributeValue>?): String? {
        if (token == null) return null
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzip ->
            val json = gson.toJson(token)
            gzip.write(json.toByteArray(StandardCharsets.UTF_8))
        }
        return java.util.Base64.getEncoder().encodeToString(byteOut.toByteArray())
    }

    @Throws(IOException::class)
    fun deserializeToken(string: String?): Map<String, AttributeValue>? {
        if (string == null) return null
        val compressedBytes = java.util.Base64.getDecoder().decode(string)
        val byteOut = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { gzip -> gzip.copyTo(byteOut) }
        val type = object : TypeToken<Map<String, AttributeValue>>() {}.type
        return gson.fromJson(byteOut.toString(StandardCharsets.UTF_8), type)
    }
}
