package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieEnumLinkResolver
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieEnumLinkResolverTest {
    private val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should resolve enum name from comment`() {
        val name = GogenieEnumLinkResolver.resolveEnumNameFromComment("// @enum(ctxKey)", profile)
        assertEquals("ctxKey", name)
    }

    @Test
    fun `should collect precise comment anchor for enum argument`() {
        val comment = "// @enum(ctxKey)"
        val anchors = GogenieEnumLinkResolver.collectCommentEnumAnchors(comment, profile)
        assertEquals(1, anchors.size)
        assertEquals("ctxKey", anchors[0].enumName)
        assertEquals("ctxKey", comment.substring(anchors[0].start, anchors[0].end))
    }

    @Test
    fun `should resolve enum target by annotation argument offset`() {
        val source = """
            // @enum(ctxKey)
            const (
                CtxKeyUserGroupIDs = 1
            )
        """.trimIndent()
        val offset = source.indexOf("ctxKey") + 2
        val target = GogenieEnumLinkResolver.resolveAtOffset(source, offset, profile)
        assertNotNull(target)
        assertEquals("ctxKey", target!!.enumName)
        assertNull(target.constName)
    }

    @Test
    fun `should resolve enum const by identifier offset`() {
        val source = """
            // @enum(ctxKey)
            const (
                CtxKeyUserGroupIDs = 1
                CtxKeyIsAdmin = 2
            )
        """.trimIndent()
        val offset = source.indexOf("CtxKeyIsAdmin") + 3
        val target = GogenieEnumLinkResolver.resolveAtOffset(source, offset, profile)
        assertNotNull(target)
        assertEquals("ctxKey", target!!.enumName)
        assertEquals("CtxKeyIsAdmin", target.constName)
    }

    @Test
    fun `should collect enum semantic ranges for argument and const names`() {
        val source = """
            // @enum(ctxKey)
            const (
                CtxKeyUserGroupIDs = 1
                CtxKeyIsAdmin = 2
            )
        """.trimIndent()
        val ranges = GogenieEnumLinkResolver.collectSemanticRanges(source, profile)
        assertTrue(ranges.any { it.kind == GogenieEnumLinkResolver.EnumSemanticRange.Kind.ENUM_ARGUMENT && source.substring(it.start, it.end) == "ctxKey" })
        assertTrue(ranges.any { it.kind == GogenieEnumLinkResolver.EnumSemanticRange.Kind.CONST_NAME && source.substring(it.start, it.end) == "CtxKeyUserGroupIDs" })
        assertTrue(ranges.any { it.kind == GogenieEnumLinkResolver.EnumSemanticRange.Kind.CONST_NAME && source.substring(it.start, it.end) == "CtxKeyIsAdmin" })
    }

    @Test
    fun `should map const blocks to corresponding enum in multi block file`() {
        val source = """
            // ctxKey 上下文键枚举,映射到internal下的enm包
            // @enum(ctxKey)
            const (
                CtxKeyUserGroupIDs = 1 // 用户组ID
                CtxKeyIsAdmin = 2 // 是否是管理员
            )
            
            // statusCommon 用户状态,映射到internal下的enm包
            // @enum(statusCommon)
            const (
                StatusCommonEnable = 1 // 启用
                StatusCommonDisabled = 2 // 禁用
            )
            
            // typePermissionUserPermit 授权用户类型
            // @enum(typePermissionUserPermit)
            const (
                TypePermissionUserPermitUser = "users" // 用户
                TypePermissionUserPermitGroup = "group" // 用户组
                TypePermissionUserPermitNone = "" // 空
            )
        """.trimIndent()

        val statusOffset = source.indexOf("StatusCommonEnable") + 2
        val statusSemantic = GogenieEnumLinkResolver.semanticRangeAtOffset(source, statusOffset, profile)
        assertNotNull(statusSemantic)
        assertEquals(GogenieEnumLinkResolver.EnumSemanticRange.Kind.CONST_NAME, statusSemantic!!.kind)
        assertEquals("statusCommon", statusSemantic.enumName)
        assertEquals("StatusCommonEnable", statusSemantic.constName)

        val typeOffset = source.indexOf("TypePermissionUserPermitGroup") + 2
        val typeSemantic = GogenieEnumLinkResolver.semanticRangeAtOffset(source, typeOffset, profile)
        assertNotNull(typeSemantic)
        assertEquals("typePermissionUserPermit", typeSemantic!!.enumName)
        assertEquals("TypePermissionUserPermitGroup", typeSemantic.constName)
    }
}
