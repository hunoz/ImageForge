package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.GetWorkspaceByIdInput
import dev.popaxe.dave.generated.model.GetWorkspaceByIdOutput
import dev.popaxe.dave.generated.model.GetWorkspaceByNameInput
import dev.popaxe.dave.generated.model.GetWorkspaceByNameOutput
import dev.popaxe.dave.generated.service.GetWorkspaceByIdOperation
import dev.popaxe.dave.generated.service.GetWorkspaceByNameOperation
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import jakarta.inject.Inject
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.smithy.java.server.RequestContext

class GetWorkspace @Inject constructor(component: AppComponent) :
    WorkspaceOperation(component), GetWorkspaceByNameOperation, GetWorkspaceByIdOperation {
    private val log: KLogger = Logging.logger(this::class)

    override fun getWorkspaceByName(
        input: GetWorkspaceByNameInput,
        context: RequestContext,
    ): GetWorkspaceByNameOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                log.info { "Getting workspace for user ${user.username} with name ${input.name()}" }
                val ws =
                    getWorkspace(
                        Key.builder().partitionValue(input.name()).sortValue(user.username).build(),
                        user.username,
                        "name-username-index",
                    )

                val response =
                    ec2.describeInstances { request: DescribeInstancesRequest.Builder ->
                        request.instanceIds(ws.cloudIdentifier)
                    }

                val status = getWsStatus(response.reservations()[0].instances()[0].state().name())

                GetWorkspaceByNameOutput.builder()
                    .id(ws.id.toString())
                    .name(ws.name)
                    .cloudIdentifier(ws.cloudIdentifier)
                    .username(ws.username)
                    .workspaceType(ws.workspaceType)
                    .cpuArchitecture(ws.cpuArchitecture)
                    .status(status)
                    .languageRuntimes(ws.languageRuntimes)
                    .packagesToInstall(ws.packagesToInstall)
                    .build()
            }
        }
    }

    override fun getWorkspaceById(
        input: GetWorkspaceByIdInput,
        context: RequestContext,
    ): GetWorkspaceByIdOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                val user = authenticate(input.token())

                log.info { "Getting workspace for user ${user.username} with id ${input.id()}" }
                val ws =
                    getWorkspace(
                        Key.builder().partitionValue(input.id()).sortValue(user.username).build(),
                        user.username,
                    )

                val response =
                    ec2.describeInstances { request: DescribeInstancesRequest.Builder ->
                        request.instanceIds(ws.cloudIdentifier)
                    }

                val status = getWsStatus(response.reservations()[0].instances()[0].state().name())

                GetWorkspaceByIdOutput.builder()
                    .id(ws.id.toString())
                    .name(ws.name)
                    .cloudIdentifier(ws.cloudIdentifier)
                    .username(ws.username)
                    .workspaceType(ws.workspaceType)
                    .cpuArchitecture(ws.cpuArchitecture)
                    .description(ws.description)
                    .status(status)
                    .build()
            }
        }
    }
}
