package dev.popaxe.dave.userapi.models.db

import dev.popaxe.dave.generated.model.CpuArchitecture
import dev.popaxe.dave.generated.model.LanguageRuntime
import dev.popaxe.dave.generated.model.WorkspaceType
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class WorkspaceTest {
    @Test
    fun `test table schema is correct`() {
        val schema = Workspace.TABLE_SCHEMA
        Assertions.assertEquals(Workspace::class.java, schema.itemType().rawClass())
    }

    @Test
    fun `test constructor behaves as expected`() {
        var ws = Workspace(
            name = "name",
            username = "username",
            cloudIdentifier = "cloudId",
            workspaceType = WorkspaceType.MICRO,
        )
        Assertions.assertEquals(UUID::class.java, ws.id.javaClass)
        Assertions.assertEquals("name", ws.name)
        Assertions.assertEquals("username", ws.username)
        Assertions.assertEquals("cloudId", ws.cloudIdentifier)
        Assertions.assertEquals(WorkspaceType.MICRO, ws.workspaceType)
        Assertions.assertTrue { ws.packagesToInstall.isEmpty() }
        Assertions.assertTrue { ws.languageRuntimes.isEmpty() }

        ws = Workspace(
            name = "name",
            username = "username",
            cloudIdentifier = "cloudId",
            workspaceType = WorkspaceType.MICRO,
            languageRuntimes = mutableListOf(LanguageRuntime.JAVA11),
            packagesToInstall = mutableListOf("package1"),
        )
        Assertions.assertTrue { ws.packagesToInstall.isNotEmpty() }
        Assertions.assertTrue { ws.languageRuntimes.isNotEmpty() }
        Assertions.assertEquals(LanguageRuntime.JAVA11, ws.languageRuntimes[0])
        Assertions.assertEquals("package1", ws.packagesToInstall[0])


    }

    @Test
    fun `test workspace operations are correct`() {
        val id = UUID.randomUUID()
        val ws = Workspace()
        ws.name = "name"
        ws.id = id
        ws.workspaceType = WorkspaceType.MICRO
        ws.cloudIdentifier = "cloudIdentifier"
        ws.username = "username"
        ws.cpuArchitecture = CpuArchitecture.X86_64
        ws.description = "description"
        ws.languageRuntimes = mutableListOf(LanguageRuntime.JAVA11)
        ws.packagesToInstall = mutableListOf()
        Assertions.assertEquals(id, ws.id)
        Assertions.assertEquals("name", ws.name)
        Assertions.assertEquals(WorkspaceType.MICRO, ws.workspaceType)
        Assertions.assertEquals("cloudIdentifier", ws.cloudIdentifier)
        Assertions.assertEquals("username", ws.username)
        Assertions.assertEquals(CpuArchitecture.X86_64, ws.cpuArchitecture)
        Assertions.assertEquals("description", ws.description)
        Assertions.assertEquals(LanguageRuntime.JAVA11, ws.languageRuntimes[0])
        Assertions.assertEquals(0, ws.packagesToInstall.size)
    }
}
