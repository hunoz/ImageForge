package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.ConflictException
import dev.popaxe.dave.generated.model.GetWorkspaceByIdInput
import dev.popaxe.dave.generated.model.GetWorkspaceByNameInput
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.testutils.BaseWorkspaceOperation
import dev.popaxe.dave.userapi.testutils.CommonUtilities
import java.util.Optional
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.Page
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.Reservation

class GetWorkspaceTest : BaseWorkspaceOperation() {
    private lateinit var operation: GetWorkspace

    @BeforeEach
    override fun setUp() {
        super.setUp()
        operation = GetWorkspace(component)
    }

    @Test
    fun `test that getting workspace by name fails and throws an exception when not found`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        Mockito.`when`(table.index(ArgumentMatchers.anyString())).thenReturn(index)

        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenThrow(ResourceNotFoundException.builder().message("Error").build())

        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }

        val iterable: SdkIterable<Page<Workspace>> = Mockito.mock()
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)

        // When there is a page of items, but the list is empty
        val stream = Mockito.mock<Stream<Page<Workspace>>>()
        Mockito.`when`(iterable.stream()).thenReturn(stream)
        Mockito.`when`(stream.findFirst()).thenReturn(java.util.Optional.empty())
        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
        val page = Mockito.mock<Page<Workspace>>()
        Mockito.`when`(stream.findFirst()).thenReturn(Optional.of(page))
        val items = Mockito.mock<List<Workspace>>()
        Mockito.`when`(page.items()).thenReturn(items)
        val itemStream = Mockito.mock<Stream<Workspace>>()
        Mockito.`when`(items.stream()).thenReturn(itemStream)
        Mockito.`when`(itemStream.findFirst()).thenReturn(Optional.empty())
        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }

        // When there are no pages
        Mockito.`when`(iterable.stream()).thenReturn(Stream.empty())
        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `test that getting workspace by name returns the correct workspace`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        val iterable = Mockito.mock<SdkIterable<Page<Workspace>>>()
        Mockito.`when`(table.index(ArgumentMatchers.anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(
                ec2Client.describeInstances(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(
                DescribeInstancesResponse.builder()
                    .reservations(
                        Reservation.builder()
                            .instances(
                                Instance.builder()
                                    .instanceId("i-12345678")
                                    .state { it.name(InstanceStateName.RUNNING) }
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        assertDoesNotThrow {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `test that getting workspace by name throws an exception when an unexpected error occurs`() {
        val index: DynamoDbIndex<Workspace> = Mockito.mock()
        Mockito.`when`(table.index(ArgumentMatchers.anyString())).thenReturn(index)
        Mockito.`when`(index.query(ArgumentMatchers.any(QueryConditional::class.java)))
            .thenThrow(ConflictException.builder().message("Error").build())

        assertThrows<dev.popaxe.dave.generated.model.InternalServerErrorException> {
            operation.getWorkspaceByName(
                GetWorkspaceByNameInput.builder().name("name").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `test that getting workspace by id fails and throws an exception when not found`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any(Key::class.java)))
            .thenThrow(ResourceNotFoundException.builder().message("Error").build())

        assertThrows<dev.popaxe.dave.generated.model.ResourceNotFoundException> {
            operation.getWorkspaceById(
                GetWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test
    fun `test that getting workspace by id returns the correct workspace`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any(Key::class.java))).thenReturn(ws)
        Mockito.`when`(
                ec2Client.describeInstances(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenReturn(
                DescribeInstancesResponse.builder()
                    .reservations(
                        Reservation.builder()
                            .instances(
                                Instance.builder()
                                    .instanceId("i-12345678")
                                    .state { it.name(InstanceStateName.RUNNING) }
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )

        assertDoesNotThrow {
            val response =
                operation.getWorkspaceById(
                    GetWorkspaceByIdInput.builder().id("id").token("token").build(),
                    CommonUtilities.FakeRequestContext(),
                )

            assertEquals(ws.id.toString(), response.id())
            assertEquals(ws.name, response.name())
            assertEquals(ws.workspaceType, response.workspaceType())
            assertEquals(ws.cloudIdentifier, response.cloudIdentifier())
            assertEquals(ws.username, response.username())
            assertEquals(ws.cpuArchitecture, response.cpuArchitecture())
            assertEquals(ws.description, response.description())
        }
    }

    @Test
    fun `test that getting workspace by id throws an exception when an unexpected error occurs`() {
        Mockito.`when`(table.getItem(ArgumentMatchers.any(Key::class.java)))
            .thenThrow(ConflictException.builder().message("Error").build())

        assertThrows<dev.popaxe.dave.generated.model.InternalServerErrorException> {
            operation.getWorkspaceById(
                GetWorkspaceByIdInput.builder().id("id").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }
}
