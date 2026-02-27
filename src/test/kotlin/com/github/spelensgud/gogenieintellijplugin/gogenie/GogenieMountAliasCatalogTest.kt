package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieMountAliasCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieMountAliasCatalogTest {
    private val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should register mount positional aliases as annotations`() {
        val fileText = """
            // @mount(config)
            // @config
        """.trimIndent()
        val augmented = GogenieMountAliasCatalog.augmentProfile(profile, fileText)
        val config = augmented.findSpec("config")
        assertNotNull(config)
        assertEquals("mount/config", config!!.commandSource)
    }

    @Test
    fun `should register mount key aliases from option style`() {
        val fileText = """
            // @mount(cache=Redis, config)
        """.trimIndent()
        val augmented = GogenieMountAliasCatalog.augmentProfile(profile, fileText)
        assertEquals("mount/cache", augmented.findSpec("cache")?.commandSource)
        assertEquals("mount/config", augmented.findSpec("config")?.commandSource)
    }

    @Test
    fun `should not override existing annotation specs`() {
        val fileText = """
            // @mount(service)
        """.trimIndent()
        val augmented = GogenieMountAliasCatalog.augmentProfile(profile, fileText)
        val service = augmented.findSpec("service")
        assertNotNull(service)
        assertTrue(service!!.commandSource != "mount/service")
    }
}
