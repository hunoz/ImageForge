package dev.popaxe.dave.userapi.dagger.components

import com.google.gson.Gson
import dagger.Component
import dev.popaxe.dave.userapi.auth.TokenHandler
import dev.popaxe.dave.userapi.dagger.modules.AppModule
import dev.popaxe.dave.userapi.dagger.modules.AwsModule
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.models.db.Workspace
import io.pebbletemplates.pebble.PebbleEngine
import jakarta.inject.Singleton
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.ec2.model.Vpc
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient

@Component(modules = [AwsModule::class, AppModule::class])
@Singleton
interface AppComponent {
    fun dynamodbClient(): DynamoDbClient

    fun dynamodbEnhancedClient(): DynamoDbEnhancedClient

    fun appConfig(): AppConfig

    fun workspaceTable(): DynamoDbTable<Workspace>

    fun tokenHandler(): TokenHandler

    fun ec2Client(): Ec2Client

    fun iamClient(): IamClient

    fun vpc(): Vpc

    fun subnet(): Subnet

    fun pebbleEngine(): PebbleEngine

    fun gson(): Gson

    fun ssmClient(): SsmClient

    fun stsClient(): StsClient

    fun region(): Region
}
