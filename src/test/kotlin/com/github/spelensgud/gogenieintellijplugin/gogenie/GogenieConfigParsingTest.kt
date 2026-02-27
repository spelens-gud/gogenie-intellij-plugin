package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieConfigLocator
import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GogenieConfigParsingTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `should follow gogenie config priority`() {
        val root = tempFolder.newFolder("project").toPath()
        Files.createDirectories(root.resolve(".gogenie"))
        Files.writeString(root.resolve(".gogenie/config.toml"), "x = 1")
        Files.writeString(root.resolve(".gogenie.yaml"), "x: 1")

        val selected = GogenieConfigLocator.find(root)
        assertNotNull(selected)
        assertTrue(selected.toString().endsWith(".gogenie/config.toml"))
    }

    @Test
    fun `should parse yaml dynamic annotations`() {
        val root = tempFolder.newFolder("yamlProject").toPath()
        val path = root.resolve(".gogenie.yaml")
        Files.writeString(
            path,
            """
            commands:
              http:
                indent: api
                api:
                  output_path: ./http/apis
                router:
                  output_path: ./http/routes
                client:
                  output_path: ./http/clients
              enum:
                indent: state
                output_path: ./internal/enm
              mount:
                name: wiring
              impl:
                indents:
                  - service: svc
                  - service: repo
            """.trimIndent(),
        )

        val cfg = GogenieConfigParser.parse(path)
        assertEquals("api", cfg.httpIndent)
        assertEquals("./http/apis", cfg.httpApiOutputPath)
        assertEquals("./http/routes", cfg.httpRouterOutputPath)
        assertEquals("./http/clients", cfg.httpClientOutputPath)
        assertEquals("state", cfg.enumIndent)
        assertEquals("./internal/enm", cfg.enumOutputPath)
        assertEquals("wiring", cfg.mountName)
        assertEquals(setOf("svc", "repo"), cfg.implServiceNames)
    }

    @Test
    fun `should parse toml dynamic annotations`() {
        val root = tempFolder.newFolder("tomlProject").toPath()
        val path = root.resolve(".gogenie.toml")
        Files.writeString(
            path,
            """
            [commands.http]
            indent = "rest"

            [commands.http.api]
            output_path = "./gen/apis"

            [commands.http.router]
            output_path = "./gen/routers"

            [commands.http.client]
            output_path = "./gen/clients"

            [commands.enum]
            indent = "choice"
            output_path = "./internal/enums"

            [commands.mount]
            name = "attach"

            [[commands.impl.indents]]
            service = "svc"

            [[commands.impl.indents]]
            service = "daoex"
            """.trimIndent(),
        )

        val cfg = GogenieConfigParser.parse(path)
        assertEquals("rest", cfg.httpIndent)
        assertEquals("./gen/apis", cfg.httpApiOutputPath)
        assertEquals("./gen/routers", cfg.httpRouterOutputPath)
        assertEquals("./gen/clients", cfg.httpClientOutputPath)
        assertEquals("choice", cfg.enumIndent)
        assertEquals("./internal/enums", cfg.enumOutputPath)
        assertEquals("attach", cfg.mountName)
        assertEquals(setOf("svc", "daoex"), cfg.implServiceNames)
    }
}
