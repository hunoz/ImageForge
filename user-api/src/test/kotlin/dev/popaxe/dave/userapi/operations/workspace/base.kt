package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.WorkspaceStatus
import dev.popaxe.dave.userapi.dagger.modules.AwsModule_SsmClientFactory.ssmClient
import dev.popaxe.dave.userapi.testutils.BaseWorkspaceOperation
import java.io.Writer
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.times
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.internal.waiters.ResponseOrException
import software.amazon.awssdk.core.waiters.WaiterResponse
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.Reservation
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import software.amazon.awssdk.services.ssm.model.GetParameterResponse
import software.amazon.awssdk.services.ssm.model.Parameter

class WorkspaceOperationTest : BaseWorkspaceOperation() {
    private lateinit var operation: WorkspaceOperation

    @BeforeEach
    override fun setUp() {
        super.setUp()
        operation = object : WorkspaceOperation(component) {}
    }

    @Test
    fun `get the latest ami`() {
        Mockito.`when`(
                ssmClient.getParameter(ArgumentMatchers.any(GetParameterRequest::class.java))
            )
            .thenReturn(
                GetParameterResponse.builder()
                    .parameter(Parameter.builder().value("ami-12345678").build())
                    .build()
            )
        Assertions.assertEquals("ami-12345678", operation.getLatestAmi(CpuArchitecture.ARM64))
    }

    @Test
    fun `test the formatted instance name is correct`() {
        Assertions.assertEquals(
            "dave-workspace-username-wsname",
            operation.getInstanceName("wsName", "username"),
        )
    }

    @Test
    fun `test the formatted instance role name is correct`() {
        Assertions.assertEquals(
            "dave-workspace-username-wsname",
            operation.getInstanceRoleName("wsName", "username"),
        )
    }

    @Test
    fun `test the formatted federated role name is correct`() {
        Assertions.assertEquals("dave-user-username", operation.getFederatedRoleName("username"))
    }

    @Test
    fun `test the created instance is correct and running`() {
        Mockito.`when`(
                ssmClient.getParameter(ArgumentMatchers.any(GetParameterRequest::class.java))
            )
            .thenReturn(
                GetParameterResponse.builder()
                    .parameter(Parameter.builder().value("ami-12345678").build())
                    .build()
            )
        Mockito.`when`(
                ec2Client.runInstances(ArgumentMatchers.any(RunInstancesRequest::class.java))
            )
            .thenReturn(
                RunInstancesResponse.builder()
                    .instances(Instance.builder().instanceId("i-12345678").build())
                    .build()
            )
        val ec2Waiter = Mockito.mock(Ec2Waiter::class.java)
        val waiterResponse: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(ec2Waiter)
        Mockito.`when`(
                ec2Waiter.waitUntilInstanceRunning(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(waiterResponse)
        Mockito.`when`(waiterResponse.matched())
            .thenReturn(
                ResponseOrException.response(
                    DescribeInstancesResponse.builder()
                        .reservations(
                            Reservation.builder()
                                .instances(Instance.builder().instanceId("i-12345678").build())
                                .build()
                        )
                        .build()
                )
            )
        Assertions.assertDoesNotThrow {
            val instance =
                operation.createInstance(
                    "wsName",
                    "username",
                    CpuArchitecture.ARM64,
                    "securityGroupId",
                )
            Assertions.assertEquals("i-12345678", instance.instanceId())
        }
        Mockito.verify(ec2Waiter, times(1))
            .waitUntilInstanceRunning(
                ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
            )

        Assertions.assertDoesNotThrow {
            val instance =
                operation.createInstance(
                    "wsName",
                    "username",
                    CpuArchitecture.X86_64,
                    "securityGroupId",
                )
            Assertions.assertEquals("i-12345678", instance.instanceId())
        }
        Mockito.verify(ec2Waiter, times(2))
            .waitUntilInstanceRunning(
                ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
            )
    }

    @Test
    fun `test the instance failed to create and throw an exception`() {
        Mockito.`when`(
                ssmClient.getParameter(ArgumentMatchers.any(GetParameterRequest::class.java))
            )
            .thenReturn(
                GetParameterResponse.builder()
                    .parameter(Parameter.builder().value("ami-12345678").build())
                    .build()
            )
        Mockito.`when`(
                ec2Client.runInstances(ArgumentMatchers.any(RunInstancesRequest::class.java))
            )
            .thenReturn(
                RunInstancesResponse.builder()
                    .instances(Instance.builder().instanceId("i-12345678").build())
                    .build()
            )
        val ec2Waiter = Mockito.mock(Ec2Waiter::class.java)
        val waiterResponse: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(ec2Waiter)
        Mockito.`when`<WaiterResponse<*>>(
                ec2Waiter.waitUntilInstanceRunning(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(waiterResponse)
        Mockito.`when`(waiterResponse.matched())
            .thenReturn(
                ResponseOrException.exception(SdkException.builder().message("Some error").build())
            )
        Assertions.assertThrows(InternalServerErrorException::class.java) {
            operation.createInstance("wsName", "username", CpuArchitecture.ARM64, "securityGroupId")
        }
    }

    @Test
    fun `test that the user data is generated correctly`() {
        Mockito.doNothing()
            .`when`(pebbleTemplate)
            .evaluate(
                ArgumentMatchers.any(Writer::class.java),
                ArgumentMatchers.any<Map<String, Any>>(),
            )
        val userData = operation.createUserData("username", "hostname")
        assertEquals("", userData)
    }

    @Test
    fun `test that generating the user data fails and throws an exception`() {
        Assertions.assertDoesNotThrow {
            Mockito.doThrow(RuntimeException("Error"))
                .`when`(pebbleTemplate)
                .evaluate(
                    ArgumentMatchers.any(Writer::class.java),
                    ArgumentMatchers.any<Map<String, Any>>(),
                )
        }

        Assertions.assertThrows(InternalServerErrorException::class.java) {
            operation.createUserData("username", "hostname")
        }
    }

    @Test
    fun `test that the workspace status is correct for the given instance state`() {
        Assertions.assertEquals(
            WorkspaceStatus.RUNNING,
            operation.getWsStatus(InstanceStateName.RUNNING),
        )
        Assertions.assertEquals(
            WorkspaceStatus.OFF,
            operation.getWsStatus(InstanceStateName.STOPPED),
        )
        Assertions.assertEquals(
            WorkspaceStatus.SHUTTING_DOWN,
            operation.getWsStatus(InstanceStateName.SHUTTING_DOWN),
        )
        Assertions.assertEquals(
            WorkspaceStatus.TERMINATED,
            operation.getWsStatus(InstanceStateName.TERMINATED),
        )
        Assertions.assertEquals(
            WorkspaceStatus.STARTING,
            operation.getWsStatus(InstanceStateName.PENDING),
        )
        Assertions.assertThrows(InternalServerErrorException::class.java) {
            operation.getWsStatus(InstanceStateName.fromValue(""))
        }
    }
}
