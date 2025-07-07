package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.ConflictException
import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.UpdateWorkspaceInput
import dev.popaxe.dave.generated.model.UpdateWorkspaceOutput
import dev.popaxe.dave.generated.model.WorkspaceStatus
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.generated.service.UpdateWorkspaceOperation
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.aws.FederatedPermissionsPolicyDocument
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.AttachVolumeRequest
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest
import software.amazon.awssdk.services.ec2.model.CreateVolumeRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.ModifyVolumeRequest
import software.amazon.awssdk.services.ec2.model.ResourceType
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TagSpecification
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import software.amazon.awssdk.services.ec2.model.Volume
import software.amazon.awssdk.services.ec2.model.VolumeType
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.smithy.java.server.RequestContext

class UpdateWorkspace(component: AppComponent) :
    WorkspaceOperation(component), UpdateWorkspaceOperation {
    private val log: KLogger = Logging.logger(this::class)

    override fun updateWorkspace(
        input: UpdateWorkspaceInput,
        context: RequestContext,
    ): UpdateWorkspaceOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                val ws: Workspace

                try {
                    ws =
                        table.getItem(
                            Key.builder()
                                .partitionValue(input.name())
                                .sortValue(user.username)
                                .build()
                        )
                } catch (e: ResourceNotFoundException) {
                    log.warn { "Workspace ${input.name()} not found" }
                    throw dev.popaxe.dave.generated.model.ResourceNotFoundException.builder()
                        .message("Workspace not found")
                        .build()
                } catch (e: Exception) {
                    log.error(e) { "Error getting workspace" }
                    throw InternalServerErrorException.builder()
                        .message("Internal Server Error")
                        .build()
                }
                val response =
                    ec2.describeInstances { request: DescribeInstancesRequest.Builder ->
                        request.instanceIds(ws.cloudIdentifier)
                    }

                if (
                    getWsStatus(response.reservations()[0].instances()[0].state().name()) !==
                        WorkspaceStatus.RUNNING
                ) {
                    throw ConflictException.builder().message("Workspace is not running").build()
                }

                log.info { "Updating workspace with name ${input.name()}" }
                val putWorkspaceResponse = putWorkspace(ws, input)
                if (putWorkspaceResponse.didReplace) {
                    updateFederatedRole(
                        user.username,
                        ws.cloudIdentifier,
                        putWorkspaceResponse.instance.instanceId(),
                    )
                }
                ws.workspaceType =
                    if (input.workspaceType() != null) input.workspaceType() else ws.workspaceType
                ws.cpuArchitecture =
                    if (input.cpuArchitecture() != null) input.cpuArchitecture()
                    else ws.cpuArchitecture
                ws.name = if (input.name() != null) input.name() else ws.name
                ws.description =
                    if (input.description() != null) input.description() else ws.description
                ws.cloudIdentifier =
                    if (putWorkspaceResponse.didReplace) putWorkspaceResponse.instance.instanceId()
                    else ws.cloudIdentifier

                table.putItem(ws)

                UpdateWorkspaceOutput.builder()
                    .id(ws.id.toString())
                    .name(ws.name)
                    .workspaceType(ws.workspaceType)
                    .cloudIdentifier(ws.cloudIdentifier)
                    .cpuArchitecture(ws.cpuArchitecture)
                    .username(ws.username)
                    .status(getWsStatus(putWorkspaceResponse.instance.state().name()))
                    .languageRuntimes(ws.languageRuntimes)
                    .packagesToInstall(ws.packagesToInstall)
                    .build()
            }
        }
    }

    private fun putWorkspace(ws: Workspace, input: UpdateWorkspaceInput): PutWorkspaceResponse {
        if (input.name() != null && input.name().isNotBlank() && input.name() != ws.name) {
            // Name changed, modify the tags of the instance
            ec2.createTags { request: CreateTagsRequest.Builder ->
                request
                    .resources(ws.cloudIdentifier)
                    .tags(Tag.builder().key("Name").value(input.name()).build())
            }
        }
        var replace = false
        if (input.cpuArchitecture() != null && input.cpuArchitecture() !== ws.cpuArchitecture) {
            // replace instance as new ami is required
            replace = true
        }
        if (input.workspaceType() != null && input.workspaceType() != ws.workspaceType) {
            // type changed, need to modify volume size and replace instance
            replace = true
            modifyVolume(ws.name, input.workspaceType())
        }
        if (input.hasLanguageRuntimes() || input.hasPackagesToInstall()) {
            replace = true
        }

        val volume =
            ec2.describeVolumes { request: DescribeVolumesRequest.Builder ->
                    request.filters(Filter.builder().name("tag:Name").values(ws.name).build())
                }
                .volumes()[0]

        if (replace) {
            val response =
                ec2.describeSecurityGroups { request: DescribeSecurityGroupsRequest.Builder ->
                    request.groupNames(getInstanceName(ws.name, ws.username))
                }
            val securityGroupId = response.securityGroups()[0].groupId()
            return PutWorkspaceResponse(
                replaceInstance(
                    ws.cloudIdentifier,
                    securityGroupId,
                    input.cpuArchitecture(),
                    input.name(),
                    ws.username,
                    volume,
                    input.languageRuntimes(),
                    input.packagesToInstall(),
                ),
                true,
            )
        } else {
            val instance: Instance =
                ec2.describeInstances { req -> req.instanceIds(ws.cloudIdentifier) }
                    .reservations()[0]
                    .instances()[0]
            return PutWorkspaceResponse(instance, false)
        }
    }

    private fun modifyVolume(wsName: String, wsType: WorkspaceType): Volume {
        val response =
            ec2.describeVolumes { request: DescribeVolumesRequest.Builder ->
                request.filters(Filter.builder().name("tag:Name").values(wsName).build())
            }

        if (response.hasVolumes()) {
            val volumeId = response.volumes()[0].volumeId()
            ec2.modifyVolume { request: ModifyVolumeRequest.Builder ->
                request
                    .volumeId(volumeId)
                    .size(WORKSPACE_SIZE_MAP[wsType])
                    .volumeType(VolumeType.GP3)
            }
            ec2.waiter().waitUntilVolumeAvailable { request: DescribeVolumesRequest.Builder ->
                request.volumeIds(volumeId)
            }
            return response.volumes()[0]
        } else {
            val createVolumeResponse =
                ec2.createVolume { request: CreateVolumeRequest.Builder ->
                    request
                        .volumeType(VolumeType.GP3)
                        .size(WORKSPACE_SIZE_MAP[wsType])
                        .tagSpecifications(
                            TagSpecification.builder()
                                .resourceType(ResourceType.VOLUME)
                                .tags(Tag.builder().key("Name").value(wsName).build())
                                .build()
                        )
                }
            ec2.waiter().waitUntilVolumeAvailable { request: DescribeVolumesRequest.Builder ->
                request.volumeIds(createVolumeResponse.volumeId())
            }
            return ec2.describeVolumes { request: DescribeVolumesRequest.Builder ->
                    request.volumeIds(createVolumeResponse.volumeId())
                }
                .volumes()[0]
        }
    }

    private fun replaceInstance(
        oldInstanceId: String,
        securityGroupId: String,
        arch: CpuArchitecture,
        wsName: String,
        username: String,
        volume: Volume,
        languageRuntimes: MutableList<LanguageRuntime> = mutableListOf(),
        packagesToInstall: MutableList<String> = mutableListOf(),
    ): Instance {
        ec2.terminateInstances(
            TerminateInstancesRequest.builder().instanceIds(oldInstanceId).build()
        )
        ec2.waiter().waitUntilInstanceTerminated { request: DescribeInstancesRequest.Builder ->
            request.instanceIds(oldInstanceId)
        }
        val instance =
            createInstance(
                wsName,
                username,
                arch,
                securityGroupId,
                languageRuntimes,
                packagesToInstall,
            )
        ec2.attachVolume { request: AttachVolumeRequest.Builder ->
            request.volumeId(volume.volumeId()).instanceId(instance.instanceId()).device("/dev/sdb")
        }
        ec2.waiter().waitUntilVolumeInUse { request: DescribeVolumesRequest.Builder ->
            request.volumeIds(volume.volumeId())
        }
        return instance
    }

    private fun updateFederatedRole(
        username: String,
        oldInstanceId: String,
        newInstanceId: String,
    ) {
        val policyResponse =
            iam.getRolePolicy { request: GetRolePolicyRequest.Builder ->
                request
                    .policyName("FederatedRolePermissions")
                    .roleName(getFederatedRoleName(username))
            }

        val document =
            gson.fromJson(
                policyResponse.policyDocument(),
                FederatedPermissionsPolicyDocument::class.java,
            )

        val resources: MutableList<String> = document.statement[0].resources
        resources.removeIf { r: String -> r.endsWith(oldInstanceId) }
        resources.add("arn:aws:ec2:" + region.id() + ":" + accountId + ":instance/" + newInstanceId)
        document.statement[0].resources.clear()
        document.statement[0].resources.addAll(resources)

        iam.putRolePolicy { request: PutRolePolicyRequest.Builder ->
            request
                .policyName("FederatedRolePermissions")
                .roleName(getFederatedRoleName(username))
                .policyDocument(gson.toJson(document))
        }
    }

    private data class PutWorkspaceResponse(val instance: Instance, val didReplace: Boolean)
}
