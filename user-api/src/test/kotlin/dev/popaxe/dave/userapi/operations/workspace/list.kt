package dev.popaxe.dave.userapi.operations.workspace

import dev.popaxe.dave.generated.model.InternalServerErrorException
import dev.popaxe.dave.generated.model.ListWorkspacesInput
import dev.popaxe.dave.userapi.dagger.modules.AwsModule_Ec2ClientFactory.ec2Client
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.testutils.BaseWorkspaceOperation
import dev.popaxe.dave.userapi.testutils.CommonUtilities
import java.util.function.Consumer
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.pagination.sync.SdkIterable
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex
import software.amazon.awssdk.enhanced.dynamodb.model.Page
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse
import software.amazon.awssdk.services.ec2.model.Instance
import software.amazon.awssdk.services.ec2.model.InstanceState
import software.amazon.awssdk.services.ec2.model.InstanceStateName
import software.amazon.awssdk.services.ec2.model.Reservation

class ListWorkspaceTest : BaseWorkspaceOperation() {
    lateinit var index: DynamoDbIndex<Workspace>
    lateinit var iterable: SdkIterable<Page<Workspace>>
    lateinit var iterator: MutableIterator<Page<Workspace>>
    lateinit var operation: ListWorkspaces

    @BeforeEach
    override fun setUp() {
        super.setUp()
        index = Mockito.mock()
        iterable = Mockito.mock()
        iterator = Mockito.mock()
        Mockito.`when`(table.index(Mockito.anyString())).thenReturn(index)
        operation = ListWorkspaces(component)
    }

    @Test
    fun `has items`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.hasNext()).thenReturn(true)
        Mockito.`when`(iterable.firstOrNull())
            .thenReturn(Page.builder(Workspace::class.java).items(listOf(ws)).build())
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
                                    .instanceId(ws.cloudIdentifier)
                                    .state(
                                        InstanceState.builder()
                                            .name(InstanceStateName.RUNNING)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        assertDoesNotThrow {
            val response =
                operation.listWorkspaces(
                    ListWorkspacesInput.builder().token("token").build(),
                    CommonUtilities.FakeRequestContext(),
                )
            assertTrue(response.items().isNotEmpty())
            assertFalse { response.hasNextPage() }
        }
    }

    @Test
    fun `has next page of items`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.hasNext()).thenReturn(true)
        Mockito.`when`(iterable.firstOrNull())
            .thenReturn(
                Page.builder(Workspace::class.java)
                    .lastEvaluatedKey(
                        mapOf<String, AttributeValue>(
                            "id" to AttributeValue.builder().s("id").build()
                        )
                    )
                    .items(listOf(ws))
                    .build()
            )
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
                                    .instanceId("id")
                                    .state(
                                        InstanceState.builder()
                                            .name(InstanceStateName.RUNNING)
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
        assertDoesNotThrow {
            val response =
                operation.listWorkspaces(
                    ListWorkspacesInput.builder().token("token").build(),
                    CommonUtilities.FakeRequestContext(),
                )
            assertTrue(response.hasNextPage())
        }
    }

    @Test
    fun `no items`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.hasNext()).thenReturn(true)
        Mockito.`when`(iterable.firstOrNull())
            .thenReturn(Page.builder(Workspace::class.java).items(listOf()).build())
        assertDoesNotThrow {
            val response =
                operation.listWorkspaces(
                    ListWorkspacesInput.builder().token("token").build(),
                    CommonUtilities.FakeRequestContext(),
                )
            assertTrue { response.items().isEmpty() }
        }
    }

    @Test
    fun `valid next token, no items`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.hasNext()).thenReturn(true)
        Mockito.`when`(iterable.firstOrNull())
            .thenReturn(Page.builder(Workspace::class.java).items(listOf()).build())
        assertDoesNotThrow {
            val response =
                operation.listWorkspaces(
                    ListWorkspacesInput.builder()
                        .token("token")
                        .nextToken(encodedNextToken)
                        .build(),
                    CommonUtilities.FakeRequestContext(),
                )
            assertTrue { response.items().isEmpty() }
            assertNull(response.nextToken())
        }
    }

    @Test
    fun `invalid next token`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.hasNext()).thenReturn(true)
        Mockito.`when`(iterable.firstOrNull())
            .thenReturn(Page.builder(Workspace::class.java).items(listOf()).build())
        assertThrows<InternalServerErrorException> {
            operation.listWorkspaces(
                ListWorkspacesInput.builder().token("token").nextToken("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }

    @Test fun `get next page`() {}

    @Test
    fun `unexpected error`() {
        Mockito.`when`(index.query(ArgumentMatchers.any<Consumer<QueryEnhancedRequest.Builder>>()))
            .thenReturn(iterable)
        Mockito.`when`(iterable.stream())
            .thenReturn(Stream.of(Page.builder(Workspace::class.java).items(listOf(ws)).build()))
        Mockito.`when`(iterable.iterator()).thenReturn(iterator)
        Mockito.`when`(iterator.next())
            .thenReturn(
                Page.builder(Workspace::class.java).items(listOf(ws)).build(),
                Page.builder(Workspace::class.java).items(listOf(ws)).build(),
            )
        Mockito.`when`(iterator.hasNext()).thenReturn(true, false)
        Mockito.`when`(
                ec2Client.describeInstances(
                    ArgumentMatchers.any<Consumer<DescribeInstancesRequest.Builder>>()
                )
            )
            .thenThrow(SdkException.builder().message("Error").build())
        assertThrows<InternalServerErrorException> {
            operation.listWorkspaces(
                ListWorkspacesInput.builder().token("token").token("token").build(),
                CommonUtilities.FakeRequestContext(),
            )
        }
    }
}
