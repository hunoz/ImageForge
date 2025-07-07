package dev.popaxe.dave.userapi.dagger.modules

import dagger.Module
import dagger.Provides
import dev.popaxe.dave.userapi.models.db.Workspace
import jakarta.inject.Singleton
import java.time.Duration
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.http.SdkHttpClient
import software.amazon.awssdk.http.crt.AwsCrtHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException
import software.amazon.awssdk.services.dynamodb.model.TableClass
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.CreateSubnetRequest
import software.amazon.awssdk.services.ec2.model.CreateVpcRequest
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsRequest
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsResponse
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest
import software.amazon.awssdk.services.ec2.model.DescribeVpcsResponse
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.ResourceType
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.ec2.model.Tag
import software.amazon.awssdk.services.ec2.model.TagSpecification
import software.amazon.awssdk.services.ec2.model.Vpc
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient

@Module
class AwsModule {
    companion object {
        private val sdkHttpClient: SdkHttpClient =
            AwsCrtHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3))
                .maxConcurrency(100)
                .build()
        private val vpcCidrBlock: String = "10.0.0.0/8"
        private val vpcTagSpecification: TagSpecification =
            TagSpecification.builder()
                .resourceType(ResourceType.VPC)
                .tags(Tag.builder().key("Name").value("dave-workspace-vpc").build())
                .build()
        private val vpcTag: Tag = vpcTagSpecification.tags().get(0)
        private val subnetCidrBlock: String = "10.0.0.0/16"
    }

    @Provides
    @Singleton
    fun dynamoDbClient(): DynamoDbClient {
        return DynamoDbClient.builder().httpClient(sdkHttpClient).build()
    }

    @Provides
    @Singleton
    fun dynamoDbEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build()
    }

    @Provides
    @Singleton
    fun workspaceTable(
        dynamoDbClient: DynamoDbClient,
        dynamoDbEnhancedClient: DynamoDbEnhancedClient,
    ): DynamoDbTable<Workspace> {
        val table: DynamoDbTable<Workspace> =
            dynamoDbEnhancedClient.table(Workspace.TABLE_NAME, Workspace.TABLE_SCHEMA)
        try {
            dynamoDbClient.describeTable(
                DescribeTableRequest.builder().tableName(Workspace.TABLE_NAME).build()
            )
        } catch (e: ResourceNotFoundException) {
            val keySchemaElements: MutableList<KeySchemaElement> = mutableListOf()
            val attributeDefinitions: MutableList<AttributeDefinition> = mutableListOf()
            keySchemaElements.add(
                KeySchemaElement.builder()
                    .attributeName(table.tableSchema().tableMetadata().primaryPartitionKey())
                    .keyType("HASH")
                    .build()
            )
            attributeDefinitions.add(
                AttributeDefinition.builder()
                    .attributeName(table.tableSchema().tableMetadata().primaryPartitionKey())
                    .attributeType("S")
                    .build()
            )
            table
                .tableSchema()
                .tableMetadata()
                .primarySortKey()
                .ifPresent({ sortKey ->
                    keySchemaElements.add(
                        KeySchemaElement.builder().attributeName(sortKey).keyType("RANGE").build()
                    )
                    attributeDefinitions.add(
                        AttributeDefinition.builder()
                            .attributeName(sortKey)
                            .attributeType("S")
                            .build()
                    )
                })

            val gsis: MutableList<GlobalSecondaryIndex> = mutableListOf()
            table
                .tableSchema()
                .tableMetadata()
                .indices()
                .forEach({ index ->
                    val gsiBuilder: GlobalSecondaryIndex.Builder =
                        GlobalSecondaryIndex.builder().indexName(index.name())
                    val gsiKeySchemaElements: MutableList<KeySchemaElement> = mutableListOf()
                    index
                        .partitionKey()
                        .ifPresent({ partitionKey ->
                            gsiKeySchemaElements.add(
                                KeySchemaElement.builder()
                                    .attributeName(partitionKey.name())
                                    .keyType("HASH")
                                    .build()
                            )
                        })
                    index
                        .sortKey()
                        .ifPresent({ sortKey ->
                            gsiKeySchemaElements.add(
                                KeySchemaElement.builder()
                                    .attributeName(sortKey.name())
                                    .keyType("RANGE")
                                    .build()
                            )
                        })
                    gsis.add(gsiBuilder.keySchema(gsiKeySchemaElements).build())
                })
            dynamoDbClient.createTable(
                CreateTableRequest.builder()
                    .tableName(Workspace.TABLE_NAME)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .keySchema(keySchemaElements)
                    .attributeDefinitions(attributeDefinitions)
                    .globalSecondaryIndexes(gsis)
                    .tableClass(TableClass.STANDARD)
                    .build()
            )
        }

        return table
    }

    @Provides
    @Singleton
    fun ec2Client(): Ec2Client {
        return Ec2Client.builder().httpClient(sdkHttpClient).build()
    }

    @Provides
    @Singleton
    fun iamClient(): IamClient {
        return IamClient.builder().httpClient(sdkHttpClient).build()
    }

    @Provides
    @Singleton
    fun vpc(ec2Client: Ec2Client): Vpc {
        return getOrCreateVpc(ec2Client)
    }

    @Provides
    @Singleton
    fun subnet(ec2Client: Ec2Client, vpc: Vpc): Subnet {
        return getOrCreateSubnet(ec2Client, vpc)
    }

    @Provides
    @Singleton
    fun ssmClient(): SsmClient {
        return SsmClient.builder().httpClient(sdkHttpClient).build()
    }

    @Provides
    @Singleton
    fun stsClient(): StsClient {
        return StsClient.builder().httpClient(sdkHttpClient).build()
    }

    @Provides
    @Singleton
    fun region(): Region {
        return DefaultAwsRegionProviderChain.builder().build().getRegion()
    }

    private fun getOrCreateVpc(ec2: Ec2Client): Vpc {
        val tagKey: String = "tag:${vpcTag.key()}"
        val response: DescribeVpcsResponse =
            ec2.describeVpcs(
                DescribeVpcsRequest.builder()
                    .filters(
                        Filter.builder().name("cidr-block").values(vpcCidrBlock).build(),
                        Filter.builder().name(tagKey).values(vpcTag.value()).build(),
                    )
                    .build()
            )
        if (response.hasVpcs()) {
            return response.vpcs().get(0)
        }

        return ec2.createVpc(
                CreateVpcRequest.builder()
                    .cidrBlock(vpcCidrBlock)
                    .tagSpecifications(vpcTagSpecification)
                    .build()
            )
            .vpc()
    }

    private fun getOrCreateSubnet(ec2: Ec2Client, vpc: Vpc): Subnet {
        val response: DescribeSubnetsResponse =
            ec2.describeSubnets(
                DescribeSubnetsRequest.builder()
                    .filters(
                        Filter.builder().name("vpc-id").values(vpc.vpcId()).build(),
                        Filter.builder().name("cidr-block").values(subnetCidrBlock).build(),
                    )
                    .build()
            )
        if (response.hasSubnets()) {
            return response.subnets().get(0)
        }

        return ec2.createSubnet(
                CreateSubnetRequest.builder().vpcId(vpc.vpcId()).cidrBlock(subnetCidrBlock).build()
            )
            .subnet()
    }
}
