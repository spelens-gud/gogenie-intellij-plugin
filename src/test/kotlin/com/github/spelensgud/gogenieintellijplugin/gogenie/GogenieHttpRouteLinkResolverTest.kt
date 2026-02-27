package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieHttpRouteLinkResolver
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GogenieHttpRouteLinkResolverTest {
    private val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should collect route value span from http annotation`() {
        val comment = "// @http(method=get,route=\"list\")"
        val anchor = GogenieHttpRouteLinkResolver.collectCommentRouteAnchors(comment, profile).single()
        assertEquals("http", anchor.annotationName)
        assertEquals("get", anchor.method)
        assertEquals("list", anchor.routeValue)
        assertEquals("list", comment.substring(anchor.start, anchor.end))
    }

    @Test
    fun `should resolve full route context from service and method annotations`() {
        val source = """
            // @service(user,route="user")
            type UserService interface {
                // @http(method=get,route="list")
                GetUserList() error
            }
        """.trimIndent()

        val commentStart = source.indexOf("// @http")
        val commentEnd = source.indexOf('\n', startIndex = commentStart).let { if (it < 0) source.length else it }
        val anchor = GogenieHttpRouteLinkResolver.collectCommentRouteAnchors(
            source.substring(commentStart, commentEnd),
            profile,
        ).single()

        val context = GogenieHttpRouteLinkResolver.resolveRouteContext(
            fileText = source,
            commentStartOffset = commentStart,
            commentEndOffset = commentEnd,
            anchor = anchor,
            profile = profile,
        )

        assertNotNull(context)
        assertEquals("user/list", context!!.fullRoute)
        assertEquals("user", context.group)
        assertEquals("user", context.filename)
        assertEquals("GetUserList", context.handler)
    }

    @Test
    fun `should use group and filename options when provided`() {
        val source = """
            // @service(session,route="auth",group="account",filename="auth_api")
            type SessionService interface {
                // @http(method=post,route="logout")
                Logout() error
            }
        """.trimIndent()

        val commentStart = source.indexOf("// @http")
        val commentEnd = source.indexOf('\n', startIndex = commentStart).let { if (it < 0) source.length else it }
        val anchor = GogenieHttpRouteLinkResolver.collectCommentRouteAnchors(
            source.substring(commentStart, commentEnd),
            profile,
        ).single()

        val context = GogenieHttpRouteLinkResolver.resolveRouteContext(
            fileText = source,
            commentStartOffset = commentStart,
            commentEndOffset = commentEnd,
            anchor = anchor,
            profile = profile,
        )

        assertNotNull(context)
        assertEquals("auth/logout", context!!.fullRoute)
        assertEquals("account", context.group)
        assertEquals("auth_api", context.filename)
        assertEquals("Logout", context.handler)
    }
}
