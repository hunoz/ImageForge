package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.DeleteWorkspaceByIdInput
import dev.popaxe.dave.generated.model.DeleteWorkspaceByIdOutput
import dev.popaxe.dave.generated.model.DeleteWorkspaceByNameInput
import dev.popaxe.dave.generated.model.DeleteWorkspaceByNameOutput
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.service.DeleteWorkspaceByIdOperation
import dev.popaxe.dave.generated.service.DeleteWorkspaceByNameOperation
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import jakarta.inject.Inject
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import software.amazon.smithy.java.server.RequestContext

class DeleteWorkspace @Inject constructor(component: AppComponent) :
    WorkspaceOperation(component), DeleteWorkspaceByIdOperation, DeleteWorkspaceByNameOperation {
    private val log: KLogger = Logging.logger(this::class)

    override fun deleteWorkspaceById(
        input: DeleteWorkspaceByIdInput,
        context: RequestContext,
    ): DeleteWorkspaceByIdOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                val key = Key.builder().partitionValue(input.id()).build()
                runDeleteOperation(key, user.username)

                DeleteWorkspaceByIdOutput.builder().build()
            }
        }
    }

    override fun deleteWorkspaceByName(
        input: DeleteWorkspaceByNameInput,
        context: RequestContext,
    ): DeleteWorkspaceByNameOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                val key =
                    Key.builder().partitionValue(input.name()).sortValue(user.username).build()
                runDeleteOperation(key, user.username, "name-username-index")

                DeleteWorkspaceByNameOutput.builder().build()
            }
        }
    }

    private fun runDeleteOperation(key: Key, username: String, indexName: String? = null) {
        try {
            val ws = getWorkspace(key, username, indexName)

            deleteWorkspace(ws, username)

            return
        } catch (_: dev.popaxe.dave.generated.model.ResourceNotFoundException) {
            return
        } catch (e: Exception) {
            log.error(e) { "Error deleting workspace" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }
    }

    private fun checkPermissions(ws: Workspace, username: String) {
        if (ws.username != username) {
            log.warn {
                "User $username attempted to delete workspace ${ws.name} for user ${ws.username}"
            }
            throw dev.popaxe.dave.generated.model.ResourceNotFoundException.builder()
                .message("Workspace not found")
                .build()
        }
    }

    private fun deleteWorkspace(ws: Workspace, username: String) {
        checkPermissions(ws, username)
        try {
            ec2.terminateInstances { request: TerminateInstancesRequest.Builder ->
                request.instanceIds(ws.cloudIdentifier)
            }
            ec2.waiter()
                .waitUntilInstanceTerminated { request: DescribeInstancesRequest.Builder ->
                    request.instanceIds(ws.cloudIdentifier)
                }
                .matched()
                .exception()
                .ifPresent {
                    log.error(it) {
                        "Error terminating workspace ${ws.name} for user ${ws.username}"
                    }
                    throw InternalServerErrorException.builder()
                        .message("Internal Server Error")
                        .build()
                }
        } catch (e: InternalServerErrorException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error terminating workspace ${ws.name} for user ${ws.username}" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }
        try {
            log.info { "Deleting workspace ${ws.name} for user ${ws.username}" }
            table.deleteItem(ws)
        } catch (e: Exception) {
            log.error(e) { "Error deleting workspace ${ws.name} for user ${ws.username}" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }
    }
}
