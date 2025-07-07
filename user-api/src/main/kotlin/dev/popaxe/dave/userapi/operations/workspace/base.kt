package dev.popaxe.dave.userapi.operations.workspace

import com.google.gson.Gson
import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceStatus
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.operations.AuthBasedOperation
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.StringWriter
import java.io.Writer
import java.util.*
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.IamInstanceProfileSpecification
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceNetworkInterfaceSpecification
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.InstanceType
import software.amazon.awssdk.services.ec2.model.RunInstancesMonitoringEnabled
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TagSpecification
import software.amazon.awssdk.services.ec2.model.Vpc
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest

abstract class WorkspaceOperation protected constructor(component: AppComponent) :
    AuthBasedOperation(component.tokenHandler()) {
    private val log: KLogger = Logging.logger(this::class)

    protected val appConfig: AppConfig = component.appConfig()
    protected val table: DynamoDbTable<Workspace> = component.workspaceTable()
    protected val ec2: Ec2Client = component.ec2Client()
    protected val iam: IamClient = component.iamClient()
    protected val gson: Gson = component.gson()
    private val ssm: SsmClient = component.ssmClient()

    protected var vpc: Vpc = component.vpc()
    protected var subnet: Subnet = component.subnet()
    protected val accountId: String = component.stsClient().callerIdentity.account()
    protected val region: Region = component.region()

    private val userDataTemplate: PebbleTemplate =
        component.pebbleEngine().getTemplate("workspace-user-data.sh.peb")

    fun getLatestAmi(architecture: CpuArchitecture): String {
        val parameter =
            String.format(
                "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-%s",
                architecture.toString().lowercase(Locale.getDefault()),
            )
        val response = ssm.getParameter(GetParameterRequest.builder().name(parameter).build())
        return response.parameter().value()
    }

    fun getInstanceName(wsName: String, username: String): String {
        return String.format(
            "dave-workspace-%s-%s",
            username.lowercase(Locale.getDefault()),
            wsName.lowercase(Locale.getDefault()),
        )
    }

    fun getInstanceRoleName(wsName: String, username: String): String {
        return String.format(
            "dave-workspace-%s-%s",
            username.lowercase(Locale.getDefault()),
            wsName.lowercase(Locale.getDefault()),
        )
    }

    fun getFederatedRoleName(username: String): String {
        return String.format("dave-user-%s", username.lowercase(Locale.getDefault()))
    }

    fun createInstance(
        wsName: String,
        username: String,
        arch: CpuArchitecture,
        securityGroupId: String?,
        languageRuntimes: MutableList<LanguageRuntime> = mutableListOf(),
        packagesToInstall: MutableList<String> = mutableListOf(),
    ): Instance {
        val name = getInstanceName(wsName, username)
        val roleName = getInstanceRoleName(wsName, username)
        val imageId = getLatestAmi(arch)
        val response =
            ec2.runInstances(
                RunInstancesRequest.builder()
                    .monitoring(RunInstancesMonitoringEnabled.builder().enabled(true).build())
                    .instanceType(CPU_ARCHITECTURE_INSTANCE_TYPE_MAP[arch])
                    .subnetId(subnet.subnetId())
                    .securityGroupIds(securityGroupId)
                    .networkInterfaces(
                        InstanceNetworkInterfaceSpecification.builder()
                            .associatePublicIpAddress(true)
                            .build()
                    )
                    .imageId(imageId)
                    .iamInstanceProfile(
                        IamInstanceProfileSpecification.builder().name(roleName).build()
                    )
                    .userData(createUserData(username, wsName, languageRuntimes, packagesToInstall))
                    .tagSpecifications(
                        TagSpecification.builder()
                            .resourceType("instance")
                            .tags(listOf(Tag.builder().key("Name").value(name).build()))
                            .build()
                    )
                    .build()
            )
        val matched =
            ec2.waiter()
                .waitUntilInstanceRunning { request: DescribeInstancesRequest.Builder ->
                    request.instanceIds(response.instances()[0].instanceId())
                }
                .matched()

        val responseOrException = Pair(matched.response(), matched.exception())
        responseOrException.second.ifPresent {
            log.error(it) { "Error creating instance" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }

        return responseOrException.first
            .orElseThrow {
                InternalServerErrorException.builder().message("Internal Server Error").build()
            }
            .reservations()
            .stream()
            .findFirst()
            .orElseThrow {
                InternalServerErrorException.builder().message("Internal Server Error").build()
            }
            .instances()
            .stream()
            .findFirst()
            .orElseThrow {
                InternalServerErrorException.builder().message("Internal Server Error").build()
            }
    }

    fun createUserData(
        username: String,
        hostname: String,
        languageRuntimes: MutableList<LanguageRuntime> = mutableListOf(),
        packagesToInstall: MutableList<String> = mutableListOf(),
    ): String {
        val writer: Writer = StringWriter()
        val languages: List<Map<String, String>> =
            languageRuntimes.map {
                mapOf("language" to it.value().split("@")[0], "version" to it.value().split("@")[1])
            }
        val context =
            mapOf(
                "username" to username,
                "hostname" to hostname,
                "languageRuntimes" to languages,
                "packagesToInstall" to packagesToInstall,
            )
        try {
            userDataTemplate.evaluate(writer, context)
            return writer.toString()
        } catch (e: Exception) {
            log.error(e) { "Error creating user data" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }
    }

    fun getWsStatus(status: InstanceStateName?): WorkspaceStatus {
        return when (status) {
            InstanceStateName.PENDING -> WorkspaceStatus.STARTING
            InstanceStateName.RUNNING -> WorkspaceStatus.RUNNING
            InstanceStateName.STOPPED -> WorkspaceStatus.OFF
            InstanceStateName.STOPPING,
            InstanceStateName.SHUTTING_DOWN -> WorkspaceStatus.SHUTTING_DOWN
            InstanceStateName.TERMINATED -> WorkspaceStatus.TERMINATED
            else -> {
                log.error { "Unknown instance state $status" }
                throw InternalServerErrorException.builder()
                    .message("Internal Server Error")
                    .build()
            }
        }
    }

    protected fun getWorkspace(key: Key, username: String, indexName: String? = null): Workspace {
        try {
            val ws: Workspace
            if (indexName != null) {
                val wsIterable = table.index(indexName).query(QueryConditional.keyEqualTo(key))
                ws =
                    wsIterable
                        .stream()
                        .findFirst()
                        .orElseThrow {
                            dev.popaxe.dave.generated.model.ResourceNotFoundException.builder()
                                .message("Workspace not found")
                                .build()
                        }
                        .items()
                        .stream()
                        .findFirst()
                        .orElseThrow {
                            dev.popaxe.dave.generated.model.ResourceNotFoundException.builder()
                                .message("Workspace not found")
                                .build()
                        }
            } else {
                ws = table.getItem(key)
            }
            log.info { "Got workspace: ${ws.id}" }
            return ws
        } catch (e: ResourceNotFoundException) {
            log.error(e) {
                "No workspace found for user $username with key ${key.partitionKeyValue().s()}"
            }
            throw dev.popaxe.dave.generated.model.ResourceNotFoundException.builder()
                .message("Workspace not found")
                .build()
        } catch (e: dev.popaxe.dave.generated.model.ResourceNotFoundException) {
            log.error(e) {
                "No workspace found for user $username with key ${key.partitionKeyValue().s()}"
            }
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error getting workspace" }
            throw InternalServerErrorException.builder().message("Internal Server Error").build()
        }
    }

    internal inline fun <T> withRequestId(requestId: String, block: () -> T): T {
        preRun(requestId)
        try {
            return block()
        } catch (e: Exception) {
            log.error(e) { "Exception encountered for request ID $requestId" }
            throw e
        } finally {
            postRun()
        }
    }

    companion object {
        protected val CPU_ARCHITECTURE_INSTANCE_TYPE_MAP: Map<CpuArchitecture, InstanceType> =
            mapOf(
                CpuArchitecture.ARM64 to InstanceType.T4_G_MEDIUM,
                CpuArchitecture.X86_64 to InstanceType.T3_MEDIUM,
            )
        @JvmStatic
        protected val WORKSPACE_SIZE_MAP: Map<WorkspaceType, Int> =
            mapOf(WorkspaceType.MICRO to 8, WorkspaceType.STANDARD to 100)
    }
}
