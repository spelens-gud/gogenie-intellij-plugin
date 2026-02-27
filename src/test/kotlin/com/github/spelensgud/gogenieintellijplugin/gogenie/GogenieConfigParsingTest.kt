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
              enum:
                indent: state
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
        assertEquals("state", cfg.enumIndent)
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

            [commands.enum]
            indent = "choice"

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
        assertEquals("choice", cfg.enumIndent)
        assertEquals("attach", cfg.mountName)
        assertEquals(setOf("svc", "daoex"), cfg.implServiceNames)
    }
}
