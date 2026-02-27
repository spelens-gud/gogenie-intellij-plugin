package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieMountAliasCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieMountLinkResolver
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieMountLinkResolverTest {
    private val baseProfile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should collect mount binding from struct`() {
        val source = """
            // @mount(config)
            type AppConfig struct {
                RedisConfig string
            }
        """.trimIndent()

        val bindings = GogenieMountLinkResolver.collectMountBindingsFromFile(source, setOf("mount"))
        assertEquals(1, bindings.size)
        val binding = bindings.single()
        assertEquals("config", binding.alias)
        assertEquals("AppConfig", binding.structName)
        assertTrue(binding.fieldOffsets.containsKey("RedisConfig"))
    }

    @Test
    fun `should collect mount value anchor from dynamic mount annotation`() {
        val comment = "// @config(config=RedisConfig)"
        val profile = GogenieMountAliasCatalog.augmentProfile(baseProfile, "// @mount(config)")
        val anchors = GogenieMountLinkResolver.collectCommentValueAnchors(comment, profile)
        assertEquals(1, anchors.size)
        val anchor = anchors.single()
        assertEquals("config", anchor.annotationName)
        assertEquals("RedisConfig", anchor.valueName)
        assertEquals("RedisConfig", comment.substring(anchor.start, anchor.end))
    }

    @Test
    fun `should ignore non identifier value`() {
        val comment = "// @config(config=\"redis/config\")"
        val profile = GogenieMountAliasCatalog.augmentProfile(baseProfile, "// @mount(config)")
        val anchors = GogenieMountLinkResolver.collectCommentValueAnchors(comment, profile)
        assertNotNull(anchors)
        assertTrue(anchors.isEmpty())
    }
}
