package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.DeleteWorkspaceByIdInput
import dev.popaxe.dave.generated.model.DeleteWorkspaceByNameInput
import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.testutils.BaseWorkspaceOperation
import dev.popaxe.dave.userapi.testutils.CommonUtilities
import dev.popaxe.dave.userapi.testutils.CommonUtilities.createUser
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.test.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.times
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.internal.waiters.ResponseOrException.exception
import software.amazon.awssdk.core.internal.waiters.ResponseOrException.response
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.core.waiters.WaiterResponse
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.Page
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter

class DeleteWorkspaceTest : BaseWorkspaceOperation() {
    private lateinit var operation: DeleteWorkspace

    @BeforeEach
    override fun setUp() {
        super.setUp()
        operation = DeleteWorkspace(component)
    }

    @Test
    fun `delete workspace by id - found`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(response.matched())
            .thenReturn(response(DescribeInstancesResponse.builder().build()))
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>())).thenReturn(null)
        assertDoesNotThrow {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `delete workspace by id - not found`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>()))
            .thenThrow(ResourceNotFoundException.builder().message("Error").build())
        assertDoesNotThrow {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, Mockito.never())
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, Mockito.never()).deleteItem(ArgumentMatchers.any<Workspace>())
    }

    @Test
    fun `delete workspace by id - failed to delete from db`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(response.matched())
            .thenReturn(response(DescribeInstancesResponse.builder().build()))
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>()))
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, times(1))
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, times(1)).deleteItem(ArgumentMatchers.any<Workspace>())
    }

    @Test
    fun `delete workspace by id - no permissions`() {
        ws.username = "other"
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        assertDoesNotThrow {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, Mockito.never())
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, Mockito.never()).deleteItem(ArgumentMatchers.any<Workspace>())
        ws.username = createUser().username
    }

    @Test
    fun `delete workspace by id - unexpected ec2 error - fail on termination`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `delete workspace by id - unexpected ec2 error - waiter response is an exception`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(TerminateInstancesResponse.builder().build())
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(response.matched())
            .thenReturn(exception(SdkException.builder().message("Error Encountered").build()))
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `delete workspace by id - unexpected ddb error`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any<Key>())).thenReturn(ws)
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>()))
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `delete workspace by name - found`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(response.matched())
            .thenReturn(response(DescribeInstancesResponse.builder().build()))
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>())).thenReturn(null)
        assertDoesNotThrow {
            operation.deleteWorkspaceByName(
                DeleteWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `delete workspace by name - not found`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenThrow(ResourceNotFoundException.builder().message("Error").build())

        assertDoesNotThrow {
            operation.deleteWorkspaceByName(
                DeleteWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, Mockito.never())
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, Mockito.never()).deleteItem(ArgumentMatchers.any<Workspace>())
    }

    @Test
    fun `delete workspace by name - failed to delete from db`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(response.matched())
            .thenReturn(response(DescribeInstancesResponse.builder().build()))
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>()))
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceByName(
                DeleteWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, times(1))
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, times(1)).deleteItem(ArgumentMatchers.any<Workspace>())
    }

    @Test
    fun `delete workspace by name - no permissions`() {
        ws.username = "other"
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        assertDoesNotThrow {
            operation.deleteWorkspaceByName(
                DeleteWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        Mockito.verify(ec2Client, Mockito.never())
            .terminateInstances(ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>())
        Mockito.verify(table, Mockito.never()).deleteItem(ArgumentMatchers.any<Workspace>())
        ws.username = createUser().username
    }

    @Test
    fun `delete workspace by name - unexpected ec2 error - fail on termination`() {
        ws.username = "other"
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        ws.username = createUser().username
    }

    @Test
    fun `delete workspace by name - unexpected ec2 error - waiter response is an exception`() {
        ws.username = "other"
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(TerminateInstancesResponse.builder().build())
        val waiter: Ec2Waiter = Mockito.mock()
        val response: WaiterResponse<DescribeInstancesResponse> = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(response)
        Mockito.`when`(response.matched())
            .thenReturn(exception(SdkException.builder().message("Error Encountered").build()))
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        ws.username = createUser().username
    }

    @Test
    fun `delete workspace by name - unexpected ddb error`() {
        ws.username = "other"
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.terminateInstances(
                    ArgumentMatchers.any<Consumer<TerminateInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        val waiter: Ec2Waiter = Mockito.mock()
        Mockito.`when`(ec2Client.waiter()).thenReturn(waiter)
        Mockito.`when`(
                waiter.waitUntilInstanceTerminated(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(null)
        Mockito.`when`(table.deleteItem(ArgumentMatchers.any<Workspace>()))
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.deleteWorkspaceById(
                DeleteWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        ws.username = createUser().username
    }
}
