package dev.popaxe.dave.userapi

import dev.popaxe.dave.generated.service.UserApi
import dev.popaxe.dave.userapi.dagger.components.AppComponent
import dev.popaxe.dave.userapi.dagger.components.DaggerAppComponent
import dev.popaxe.dave.userapi.operations.GetAuthenticationInformation
import dev.popaxe.dave.userapi.operations.workspace.CreateWorkspace
import dev.popaxe.dave.userapi.operations.workspace.DeleteWorkspace
import dev.popaxe.dave.userapi.operations.workspace.GetWorkspace
import dev.popaxe.dave.userapi.operations.workspace.ListWorkspaces
import dev.popaxe.dave.userapi.operations.workspace.UpdateWorkspace
import dev.popaxe.dave.userapi.utils.Logging
import io.github.oshai.kotlinlogging.KLogger
import java.net.URI
import java.util.concurrent.ExecutionException
import software.amazon.smithy.java.server.Server

class UserApiServer : Runnable {
    private val log: KLogger = Logging.logger(this::class)

    override fun run() {
        val component: AppComponent = DaggerAppComponent.create()
        val server =
            Server.builder()
                .endpoints(endpoint)
                .addService(
                    UserApi.builder()
                        .addCreateWorkspaceOperation(CreateWorkspace(component))
                        .addDeleteWorkspaceByIdOperation(DeleteWorkspace(component))
                        .addDeleteWorkspaceByNameOperation(DeleteWorkspace(component))
                        .addGetAuthenticationInformationOperation(
                            GetAuthenticationInformation(component.appConfig())
                        )
                        .addGetWorkspaceByIdOperation(GetWorkspace(component))
                        .addGetWorkspaceByNameOperation(GetWorkspace(component))
                        .addListWorkspacesOperation(ListWorkspaces(component))
                        .addUpdateWorkspaceOperation(UpdateWorkspace(component))
                        .build()
                )
                .numberOfWorkers(10)
                .build()
        log.info { "Starting server..." }
        server.start()
        try {
            Thread.currentThread().join()
        } catch (e: InterruptedException) {
            log.info { "Stopping server..." }
            try {
                server.shutdown().get()
            } catch (ex: InterruptedException) {
                throw RuntimeException(ex)
            } catch (ex: ExecutionException) {
                throw RuntimeException(ex)
            }
        }
    }

    companion object {
        val endpoint: URI = URI.create("http://localhost:8443")

        @JvmStatic
        fun main(args: Array<String>) {
            UserApiServer().run()
        }
    }
}

fun main(args: Array<String>) {
    UserApiServer.main(args)
}
