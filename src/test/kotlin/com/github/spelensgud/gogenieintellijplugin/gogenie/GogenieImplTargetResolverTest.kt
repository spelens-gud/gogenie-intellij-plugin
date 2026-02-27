package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieImplTargetResolver
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GogenieImplTargetResolverTest {
    private val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should resolve impl annotation from comment`() {
        val annotation = GogenieImplTargetResolver.resolveImplAnnotation("// @service(user,route=\"user\")", profile)
        assertEquals("service", annotation)
    }

    @Test
    fun `should ignore non impl annotation from comment`() {
        val annotation = GogenieImplTargetResolver.resolveImplAnnotation("// @http(method=get,route=\"/list\")", profile)
        assertNull(annotation)
    }

    @Test
    fun `should resolve following interface name`() {
        val comment = "// @service(user,route=\"user\")"
        val source = """
            $comment
            type UserService interface {
            }
        """.trimIndent()
        val commentEnd = source.indexOf(comment) + comment.length
        val interfaceName = GogenieImplTargetResolver.resolveFollowingInterfaceName(source, commentEnd)
        assertEquals("UserService", interfaceName)
    }
}

