package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.ConflictException
import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.CreateWorkspaceInput
import dev.popaxe.dave.generated.model.CreateWorkspaceOutput
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.generated.service.CreateWorkspaceOperation
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.aws.FederatedPermissionsPolicyDocument
import dev.popaxe.dave.userapi.models.aws.Statement
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import jakarta.inject.Inject
import java.util.*
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.AttachVolumeRequest
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest
import software.amazon.awssdk.services.ec2.model.CreateVolumeRequest
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.ResourceType
import software.amazon.awssdk.services.ec2.model.SecurityGroup
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TagSpecification
import software.amazon.awssdk.services.ec2.model.VolumeType
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.CreateOpenIdConnectProviderRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest
import software.amazon.awssdk.services.iam.model.NoSuchEntityException
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.smithy.java.server.RequestContext

class CreateWorkspace @Inject constructor(component: AppComponent) :
    WorkspaceOperation(component), CreateWorkspaceOperation {
    private val log: KLogger = Logging.logger(this::class)

    override fun createWorkspace(
        input: CreateWorkspaceInput,
        context: RequestContext,
    ): CreateWorkspaceOutput {
        return withRequestId(context.requestId) {
            withAuthentication(input.token()) { user ->
                try {
                    log.info { "Checking if workspace ${input.name()} already exists" }
                    table.getItem(
                        Key.builder().partitionValue(input.name()).sortValue(user.username).build()
                    )
                    // Workspace exists, throw error
                    throw ConflictException.builder().message("Workspace already exists").build()
                } catch (_: ResourceNotFoundException) {
                    log.info { "Workspace ${input.name()} does not exist" }
                } catch (e: Exception) {
                    log.error(e) { "Error checking workspace existence" }
                    throw InternalServerErrorException.builder()
                        .message("Internal Server Error")
                        .build()
                }

                log.info { "Workspace ${input.name()} does not exist, creating it" }
                val instance =
                    createEc2Instance(
                        input.name(),
                        user.username,
                        input.workspaceType(),
                        input.cpuArchitecture(),
                        createSecurityGroup(input.name(), user.username).groupId(),
                        input.languageRuntimes(),
                        input.packagesToInstall(),
                    )

                val ws = Workspace()
                ws.id = UUID.randomUUID()
                ws.workspaceType = input.workspaceType()
                ws.name = input.name()
                ws.cloudIdentifier = instance.instanceId()
                ws.cpuArchitecture = input.cpuArchitecture()
                ws.description = input.description()
                ws.username = user.username

                val instanceArn =
                    String.format(
                        "arn:aws:ec2:%s:%s:instance/%s",
                        region.id(),
                        accountId,
                        instance.instanceId(),
                    )
                createOrUpdateFederatedIamRoleIfRequired(user.username, instanceArn)

                table.putItem(ws)
                log.info { "Created workspace ${ws.id}" }
                CreateWorkspaceOutput.builder()
                    .name(ws.name)
                    .workspaceType(ws.workspaceType)
                    .cpuArchitecture(ws.cpuArchitecture)
                    .description(ws.description)
                    .id(ws.id.toString())
                    .username(ws.username)
                    .cloudIdentifier(ws.cloudIdentifier)
                    .status(getWsStatus(instance.state().name()))
                    .languageRuntimes(ws.languageRuntimes)
                    .packagesToInstall(ws.packagesToInstall)
                    .build()
            }
        }
    }

    private fun createOrUpdateFederatedIamRoleIfRequired(username: String, instanceArn: String) {
        val roleName = getFederatedRoleName(username)
        var oidcProviderArn: String? = null
        val oidcResponse = iam.listOpenIDConnectProviders()
        for (oidc in oidcResponse.openIDConnectProviderList()) {
            if (oidc.arn().endsWith(appConfig.auth.domain)) {
                oidcProviderArn = oidc.arn()
            }
        }

        if (oidcProviderArn == null) {
            oidcProviderArn =
                iam.createOpenIDConnectProvider {
                        request: CreateOpenIdConnectProviderRequest.Builder ->
                        request
                            .clientIDList(appConfig.auth.clientId)
                            .url("https://${appConfig.auth.domain}")
                    }
                    .openIDConnectProviderArn()
        }
        val assumeRoleDocument =
            mapOf(
                "Version" to "2012-10-17",
                "Statement" to
                    listOf(
                        mapOf(
                            "Effect" to "Allow",
                            "Principal" to mapOf("Federated" to listOf(oidcProviderArn!!)),
                            "Action" to listOf("sts:AssumeRoleWithWebIdentity"),
                            "Condition" to
                                mapOf(
                                    "StringEquals" to
                                        mapOf(
                                            "${appConfig.auth.domain}:${appConfig.auth.usernameClaim}" to
                                                username
                                        )
                                ),
                        )
                    ),
            )

        val document = FederatedPermissionsPolicyDocument("2012-10-17", mutableListOf())

        val permissions =
            mutableListOf(
                "ssm:TerminateSession",
                "ssm:StartSession",
                "ssm:GetConnectionStatus",
                "ssm:DescribeSessions",
            )
        val resources: MutableList<String> = mutableListOf()
        resources.add(instanceArn)
        try {
            val response =
                iam.getRolePolicy { request: GetRolePolicyRequest.Builder ->
                    request.roleName(roleName).policyName("FederatedRolePermissions")
                }
            val policy =
                gson.fromJson(
                    response.policyDocument(),
                    FederatedPermissionsPolicyDocument::class.java,
                )
            resources.addAll(policy.statement[0].resources)
            document.statement.addAll(listOf(Statement("Allow", permissions, resources)))
        } catch (e: NoSuchEntityException) {
            log.info { "Creating federated IAM role for user $username" }
            iam.createRole { request: CreateRoleRequest.Builder ->
                request.roleName(roleName).assumeRolePolicyDocument(gson.toJson(assumeRoleDocument))
            }
        }

        iam.putRolePolicy { request: PutRolePolicyRequest.Builder ->
            request
                .roleName(roleName)
                .policyName("FederatedRolePermissions")
                .policyDocument(gson.toJson(document))
        }
    }

    private fun createSecurityGroup(wsName: String, username: String): SecurityGroup {
        val response =
            ec2.describeSecurityGroups { request: DescribeSecurityGroupsRequest.Builder ->
                request.groupNames(getInstanceName(wsName, username))
            }
        if (response.hasSecurityGroups()) {
            return response.securityGroups()[0]
        }

        val sgResponse =
            ec2.createSecurityGroup { request: CreateSecurityGroupRequest.Builder ->
                request.groupName(getInstanceName(wsName, username)).vpcId(vpc.vpcId())
            }

        return ec2.describeSecurityGroups { request: DescribeSecurityGroupsRequest.Builder ->
                request.groupIds(sgResponse.groupId())
            }
            .securityGroups()[0]
    }

    private fun createEc2Instance(
        wsName: String,
        username: String,
        type: WorkspaceType,
        arch: CpuArchitecture,
        securityGroupId: String,
        languageRuntimes: MutableList<LanguageRuntime> = mutableListOf(),
        packagesToInstall: MutableList<String> = mutableListOf(),
    ): Instance {
        val name = getInstanceName(wsName, username)
        val roleName = getInstanceRoleName(wsName, username)
        val imageId = getLatestAmi(arch)
        log.info { "Creating IAM role for workspace $wsName" }
        iam.createRole { request: CreateRoleRequest.Builder ->
            request
                .roleName(roleName)
                .assumeRolePolicyDocument(
                    gson.toJson(
                        mapOf(
                            "Version" to "2012-10-17",
                            "Statement" to
                                listOf(
                                    mapOf(
                                        "Effect" to "Allow",
                                        "Principal" to
                                            mapOf("Service" to listOf("ec2.amazonaws.com")),
                                        "Action" to listOf("sts:AssumeRole"),
                                    )
                                ),
                        )
                    )
                )
        }
        log.info { "Attaching SSM managed instance policy to IAM role for workspace $wsName" }
        iam.attachRolePolicy { request: AttachRolePolicyRequest.Builder ->
            request
                .roleName(roleName)
                .policyArn("arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCor")
        }
        log.info { "Creating instance profile for workspace $wsName with role $roleName" }
        iam.createInstanceProfile { request: CreateInstanceProfileRequest.Builder ->
            request.instanceProfileName(name)
        }
        log.info {
            "Attaching IAM role to instance profile for workspace $wsName with role $roleName"
        }
        iam.addRoleToInstanceProfile { request: AddRoleToInstanceProfileRequest.Builder ->
            request.instanceProfileName(name).roleName(name)
        }
        log.info { "Creating volume for workspace $wsName" }
        val volumeResponse =
            ec2.createVolume { request: CreateVolumeRequest.Builder ->
                request
                    .availabilityZone(subnet.availabilityZone())
                    .volumeType(VolumeType.GP3)
                    .size(WORKSPACE_SIZE_MAP[type])
                    .tagSpecifications(
                        TagSpecification.builder()
                            .resourceType(ResourceType.VOLUME)
                            .tags(listOf(Tag.builder().key("Name").value(name).build()))
                            .build()
                    )
            }
        log.info {
            "Waiting for volume ${volumeResponse.volumeId()} to become available (may take a few minutes) ..."
        }
        ec2.waiter()
            .waitUntilVolumeAvailable(
                DescribeVolumesRequest.builder().volumeIds(volumeResponse.volumeId()).build()
            )
        log.info { "Volume ${volumeResponse.volumeId()} is available" }
        log.info { "Creating EC2 instance with image id $imageId" }
        val instance =
            createInstance(
                wsName,
                username,
                arch,
                securityGroupId,
                languageRuntimes,
                packagesToInstall,
            )
        log.info { "Attaching volume ${volumeResponse.volumeId()} to EC2 instance" }
        ec2.attachVolume { request: AttachVolumeRequest.Builder ->
            request
                .volumeId(volumeResponse.volumeId())
                .instanceId(instance.instanceId())
                .device("/dev/sdb")
        }
        log.info { "Created EC2 instance: ${instance.instanceId()}" }
        return instance
    }
}
