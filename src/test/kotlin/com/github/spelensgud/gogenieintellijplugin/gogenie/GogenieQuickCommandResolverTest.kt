package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieQuickCommandResolver
import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieQuickCommandSpec
import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieMountAliasCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieQuickCommandResolverTest {
    private val defaultProfile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should resolve autowire command`() {
        val command = GogenieQuickCommandResolver.resolve("autowire", defaultProfile)
        assertNotNull(command)
        assertEquals(listOf("autowire"), command!!.args)
    }

    @Test
    fun `should resolve rule command with file scope`() {
        val command = GogenieQuickCommandResolver.resolve("rule", defaultProfile)
        assertNotNull(command)
        assertEquals(listOf("rule"), command!!.args)
        assertEquals("--scope", command.scopeFlag)
        assertEquals(GogenieQuickCommandSpec.ScopeTarget.FILE, command.scopeTarget)
    }

    @Test
    fun `should not resolve impl annotation as quick command`() {
        val command = GogenieQuickCommandResolver.resolve("service", defaultProfile)
        assertNull(command)
    }

    @Test
    fun `should resolve dynamic http indent when not impl annotation`() {
        val profile = GogenieAnnotationCatalog.build(
            GogenieDynamicConfig(
                httpIndent = "api",
                enumIndent = "enum",
                mountName = "mount",
                implServiceNames = setOf("service", "dao", "grpc"),
            ),
        )
        val command = GogenieQuickCommandResolver.resolve("api", profile)
        assertNotNull(command)
        assertEquals("http router", command!!.commandLabel)
        assertEquals(listOf("http", "router"), command.args)
    }

    @Test
    fun `should resolve http annotation to api and client commands`() {
        val commands = GogenieQuickCommandResolver.resolveAll("http", defaultProfile)
        assertEquals(2, commands.size)
        assertTrue(commands.any { it.commandLabel == "http api" && it.args == listOf("http", "api") })
        assertTrue(commands.any { it.commandLabel == "http client" && it.args == listOf("http", "client") })
    }

    @Test
    fun `should resolve mount alias command from mount registration`() {
        val profile = GogenieMountAliasCatalog.augmentProfile(defaultProfile, "// @mount(config)")
        val base = GogenieQuickCommandResolver.resolve("mount", profile)
        val alias = GogenieQuickCommandResolver.resolve("config", profile)
        assertNotNull(base)
        assertNotNull(alias)
        assertEquals("mount", base!!.commandLabel)
        assertEquals(listOf("mount"), base.args)
        assertEquals("mount config", alias!!.commandLabel)
        assertEquals(listOf("mount", "config"), alias.args)
    }
}
