package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.UpdateWorkspaceInput
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.userapi.models.aws.FederatedPermissionsPolicyDocument
import dev.popaxe.dave.userapi.models.aws.Statement
import dev.popaxe.dave.userapi.testutils.BaseWorkspaceOperation
import dev.popaxe.dave.userapi.testutils.CommonUtilities
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.*
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest
import software.amazon.awssdk.services.iam.model.GetRolePolicyResponse
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import java.util.function.Consumer

class UpdateWorkspaceTest : BaseWorkspaceOperation() {
    private lateinit var operation: UpdateWorkspace

    @BeforeEach
    override fun setUp() {
        super.setUp()
        operation = UpdateWorkspace(component)
    }

    @Test
    fun `happy path`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .description("new description")
            .build()

        val instance = Instance.builder()
            .instanceId("cloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .build()

        val reservation = Reservation.builder().instances(instance).build()
        val describeInstancesResponse = DescribeInstancesResponse.builder().reservations(reservation).build()

        `when`(table.getItem(any(Key::class.java))).thenReturn(ws)
        `when`(ec2Client.describeInstances(any(Consumer::class.java) as Consumer<DescribeInstancesRequest.Builder>))
            .thenReturn(describeInstancesResponse)

        val result = operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())

        assertNotNull(result)
        assertEquals(ws.id.toString(), result.id())
        assertEquals("name", result.name())
        assertEquals("new description", ws.description)
    }

    @Test
    fun `happy path with instance replacement`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .cpuArchitecture(CpuArchitecture.ARM64)
            .build()

        val oldInstance = Instance.builder()
            .instanceId("oldCloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .build()

        val newInstance = Instance.builder()
            .instanceId("newCloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .build()

        val reservation = Reservation.builder().instances(oldInstance).build()
        val describeInstancesResponse = DescribeInstancesResponse.builder().reservations(reservation).build()
        val volume = Volume.builder().volumeId("volumeId").build()
        val describeVolumesResponse = DescribeVolumesResponse.builder().volumes(volume).build()
        val securityGroup = SecurityGroup.builder().groupId("sg-12345").build()
        val describeSecurityGroupsResponse = DescribeSecurityGroupsResponse.builder().securityGroups(securityGroup).build()
        val getRolePolicyResponse = GetRolePolicyResponse.builder()
            .policyDocument(gson.toJson(FederatedPermissionsPolicyDocument(
                "2012-10-17",
                mutableListOf(Statement("Allow", mutableListOf("sts:AssumeRole"), mutableListOf("arn:aws:ec2:us-east-1:123456789012:instance/oldCloudIdentifier")))
            )))
            .build()

        `when`(table.getItem(any(Key::class.java))).thenReturn(ws)
        `when`(ec2Client.describeInstances(any(Consumer::class.java) as Consumer<DescribeInstancesRequest.Builder>))
            .thenReturn(describeInstancesResponse, DescribeInstancesResponse.builder().reservations(Reservation.builder().instances(newInstance).build()).build())
        `when`(ec2Client.describeVolumes(any(Consumer::class.java) as Consumer<DescribeVolumesRequest.Builder>)).thenReturn(describeVolumesResponse)
        `when`(ec2Client.describeSecurityGroups(any(Consumer::class.java) as Consumer<DescribeSecurityGroupsRequest.Builder>)).thenReturn(describeSecurityGroupsResponse)
        `when`(iamClient.getRolePolicy(any(Consumer::class.java) as Consumer<GetRolePolicyRequest.Builder>)).thenReturn(getRolePolicyResponse)
        `when`(ec2Client.runInstances(any(Consumer::class.java) as Consumer<RunInstancesRequest.Builder>)).thenReturn(RunInstancesResponse.builder().instances(newInstance).build())
        `when`(ec2Client.waiter()).thenReturn(Ec2Waiter.builder().client(ec2Client).build())

        val result = operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())

        assertNotNull(result)
        assertEquals(ws.id.toString(), result.id())
        assertEquals("newCloudIdentifier", result.cloudIdentifier())
        verify(ec2Client).terminateInstances(any(Consumer::class.java) as Consumer<TerminateInstancesRequest.Builder>)
        verify(iamClient).putRolePolicy(any(Consumer::class.java) as Consumer<PutRolePolicyRequest.Builder>)
    }

    @Test
    fun `workspace not found`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .build()

        `when`(table.getItem(any(Key::class.java))).thenThrow(ResourceNotFoundException.builder().build())

        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())
        }
    }

    @Test
    fun `workspace not running`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .build()

        val instance = Instance.builder()
            .instanceId("cloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.STOPPED).build())
            .build()

        val reservation = Reservation.builder().instances(instance).build()
        val describeInstancesResponse = DescribeInstancesResponse.builder().reservations(reservation).build()

        `when`(table.getItem(any(Key::class.java))).thenReturn(ws)
        `when`(ec2Client.describeInstances(any(Consumer::class.java) as Consumer<DescribeInstancesRequest.Builder>))
            .thenReturn(describeInstancesResponse)

        assertThrows<dev.popaxe.dave.generated.model.ConflictException> {
            operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())
        }
    }

    @Test
    fun `general error getting workspace`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .build()

        `when`(table.getItem(any(Key::class.java))).thenThrow(RuntimeException("Something went wrong"))

        assertThrows<dev.popaxe.dave.generated.model.InternalServerErrorException> {
            operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())
        }
    }

    @Test
    fun `happy path with volume modification`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .workspaceType(WorkspaceType.STANDARD)
            .build()

        val instance = Instance.builder()
            .instanceId("cloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .build()

        val reservation = Reservation.builder().instances(instance).build()
        val describeInstancesResponse = DescribeInstancesResponse.builder().reservations(reservation).build()
        val volume = Volume.builder().volumeId("volumeId").build()
        val describeVolumesResponse = DescribeVolumesResponse.builder().volumes(volume).build()

        `when`(table.getItem(any(Key::class.java))).thenReturn(ws)
        `when`(ec2Client.describeInstances(any(Consumer::class.java) as Consumer<DescribeInstancesRequest.Builder>))
            .thenReturn(describeInstancesResponse)
        `when`(ec2Client.describeVolumes(any(Consumer::class.java) as Consumer<DescribeVolumesRequest.Builder>)).thenReturn(describeVolumesResponse)
        `when`(ec2Client.waiter()).thenReturn(Ec2Waiter.builder().client(ec2Client).build())

        operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())

        verify(ec2Client).modifyVolume(any(Consumer::class.java) as Consumer<ModifyVolumeRequest.Builder>)
    }

    @Test
    fun `happy path with new volume`() {
        val input = UpdateWorkspaceInput.builder()
            .name("name")
            .token("token")
            .workspaceType(WorkspaceType.STANDARD)
            .build()

        val instance = Instance.builder()
            .instanceId("cloudIdentifier")
            .state(InstanceState.builder().name(InstanceStateName.RUNNING).build())
            .build()

        val reservation = Reservation.builder().instances(instance).build()
        val describeInstancesResponse = DescribeInstancesResponse.builder().reservations(reservation).build()
        val describeVolumesResponse = DescribeVolumesResponse.builder().build() // No volumes
        val createVolumeResponse = CreateVolumeResponse.builder().volumeId("newVolumeId").build()
        val createdVolume = Volume.builder().volumeId("newVolumeId").build()
        val describeCreatedVolumeResponse = DescribeVolumesResponse.builder().volumes(createdVolume).build()

        `when`(table.getItem(any(Key::class.java))).thenReturn(ws)
        `when`(ec2Client.describeInstances(any(Consumer::class.java) as Consumer<DescribeInstancesRequest.Builder>))
            .thenReturn(describeInstancesResponse)
        `when`(ec2Client.describeVolumes(any(Consumer::class.java) as Consumer<DescribeVolumesRequest.Builder>))
            .thenReturn(describeVolumesResponse, describeCreatedVolumeResponse)
        `when`(ec2Client.createVolume(any(Consumer::class.java) as Consumer<CreateVolumeRequest.Builder>))
            .thenReturn(createVolumeResponse)
        `when`(ec2Client.waiter()).thenReturn(Ec2Waiter.builder().client(ec2Client).build())

        operation.updateWorkspace(input, CommonUtilities.FakeRequestContext())

        verify(ec2Client).createVolume(any(Consumer::class.java) as Consumer<CreateVolumeRequest.Builder>)
    }
}