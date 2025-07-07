package dev.popaxe.dave.userapi.testutils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.WorkspaceType
import dev.popaxe.dave.userapi.auth.TokenHandler
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.dagger.modules.AppModule_AppConfigFactory.appConfig
import dev.popaxe.dave.userapi.models.config.AppConfig
import dev.popaxe.dave.userapi.models.db.Workspace
import dev.popaxe.dave.userapi.testutils.CommonUtilities.createUser
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.Writer
import java.util.UUID
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.Subnet
import software.amazon.awssdk.services.ec2.model.Vpc
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse

abstract class BaseWorkspaceOperation {
    protected val ws =
        Workspace().apply {
            id = UUID.randomUUID()
            name = "name"
            workspaceType = WorkspaceType.MICRO
            cloudIdentifier = "cloudIdentifier"
            username = "username"
            cpuArchitecture = CpuArchitecture.X86_64
            description = "description"
            username = "username"
        }

    protected final val encodedNextToken: String =
        StringBuilder()
            .append("H4sIAAAAAAAA/6tWykxRsqpWKlayArF0lPKUrPJK")
            .append("c3J0lJJgjGKgXHQsUAZKJ0HpXKC+Wh2lHKhofn4OTAeQhDFLKgtSgUYHK9XWAgD0P01nawAAAA==")
            .toString()

    protected lateinit var appConfig: AppConfig

    protected lateinit var tokenHandler: TokenHandler

    protected lateinit var ec2Client: Ec2Client

    protected lateinit var iamClient: IamClient

    protected lateinit var vpc: Vpc

    protected lateinit var subnet: Subnet

    protected val gson: Gson = GsonBuilder().serializeNulls().create()

    protected lateinit var ssmClient: SsmClient

    protected lateinit var stsClient: StsClient

    protected lateinit var region: Region

    protected lateinit var pebbleEngine: PebbleEngine

    protected lateinit var pebbleTemplate: PebbleTemplate

    protected lateinit var table: DynamoDbTable<Workspace>

    protected lateinit var component: AppComponent

    protected open fun setUp() {
        appConfig = Mockito.mock(AppConfig::class.java)
        pebbleEngine = Mockito.mock(PebbleEngine::class.java)
        pebbleTemplate = Mockito.mock(PebbleTemplate::class.java)
        ec2Client = Mockito.mock(Ec2Client::class.java)
        iamClient = Mockito.mock(IamClient::class.java)
        vpc = Mockito.mock(Vpc::class.java)
        subnet = Mockito.mock(Subnet::class.java)
        stsClient = Mockito.mock(StsClient::class.java)
        ssmClient = Mockito.mock(SsmClient::class.java)
        region = Mockito.mock(Region::class.java)
        tokenHandler = Mockito.mock(TokenHandler::class.java)
        table = Mockito.mock()
        component = Mockito.mock(AppComponent::class.java)
        Mockito.`when`(component.appConfig()).thenReturn(appConfig)
        Mockito.`when`(component.pebbleEngine()).thenReturn(pebbleEngine)
        Mockito.`when`(component.gson()).thenReturn(gson)
        Mockito.`when`(component.ec2Client()).thenReturn(ec2Client)
        Mockito.`when`(component.iamClient()).thenReturn(iamClient)
        Mockito.`when`(component.vpc()).thenReturn(vpc)
        Mockito.`when`(component.subnet()).thenReturn(subnet)
        Mockito.`when`(component.stsClient()).thenReturn(stsClient)
        Mockito.`when`(component.ssmClient()).thenReturn(ssmClient)
        Mockito.`when`(component.region()).thenReturn(region)
        Mockito.`when`(component.tokenHandler()).thenReturn(tokenHandler)
        Mockito.`when`(component.workspaceTable()).thenReturn(table)
        Mockito.`when`(pebbleEngine.getTemplate(ArgumentMatchers.anyString()))
            .thenReturn(pebbleTemplate)
        Mockito.`when`(stsClient.callerIdentity)
            .thenReturn(GetCallerIdentityResponse.builder().account("accountId").build())
        Mockito.doNothing()
            .`when`(pebbleTemplate)
            .evaluate(
                ArgumentMatchers.any(Writer::class.java),
                ArgumentMatchers.any<Map<String, Any>>(),
            )
        Mockito.`when`(tokenHandler.handle(ArgumentMatchers.anyString())).thenReturn(createUser())
    }
}
