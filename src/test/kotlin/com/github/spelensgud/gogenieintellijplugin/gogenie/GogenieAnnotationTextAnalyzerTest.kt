package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieAnnotationTextAnalyzer
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieAnnotationTextAnalyzerTest {
    private val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

    @Test
    fun `should detect annotation name completion context`() {
        val text = "// @htt"
        val context = GogenieAnnotationTextAnalyzer.resolveCompletionContext(text, text.length)
        assertTrue(context is GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationName)
        context as GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationName
        assertEquals("htt", context.typedPrefix)
    }

    @Test
    fun `should detect annotation option completion context`() {
        val text = "// @http(me"
        val context = GogenieAnnotationTextAnalyzer.resolveCompletionContext(text, text.length)
        assertTrue(context is GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationOption)
        context as GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationOption
        assertEquals("http", context.annotationName)
        assertEquals("me", context.typedPrefix)
    }

    @Test
    fun `should skip email like syntax`() {
        val text = "// contact: test@example.com"
        val context = GogenieAnnotationTextAnalyzer.resolveCompletionContext(text, text.length)
        assertEquals(GogenieAnnotationTextAnalyzer.CompletionContext.None, context)
    }

    @Test
    fun `should mark unknown annotations as unrecognized`() {
        val matches = GogenieAnnotationTextAnalyzer.extractAnnotations(
            "// @service(route=\"/v1\")\n// @unknown(foo=1)",
            profile,
        )
        val service = matches.first { it.name == "service" }
        val unknown = matches.first { it.name == "unknown" }
        assertTrue(service.recognized)
        assertTrue(service.options.any { it.key == "route" })
        assertTrue(!unknown.recognized)
    }
}
