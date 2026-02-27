package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieAnnotationCatalogTest {
    @Test
    fun `should include defaults in profile`() {
        val profile = GogenieAnnotationCatalog.build(GogenieDynamicConfig.defaults())

        assertNotNull(profile.findSpec("service"))
        assertNotNull(profile.findSpec("dao"))
        assertNotNull(profile.findSpec("grpc"))
        assertNotNull(profile.findSpec("http"))
        assertNotNull(profile.findSpec("autowire"))
        assertNotNull(profile.findSpec("rule"))
        assertNotNull(profile.findSpec("enum"))
        assertNotNull(profile.findSpec("mount"))
        assertNotNull(profile.findSpec("Summary"))
        assertNotNull(profile.findSpec("Router"))
        assertTrue(profile.isImplAnnotation("service"))
        assertTrue(profile.isImplAnnotation("dao"))
        assertTrue(profile.isImplAnnotation("grpc"))
        assertFalse(profile.isImplAnnotation("http"))
    }

    @Test
    fun `should merge dynamic service-like annotations`() {
        val profile = GogenieAnnotationCatalog.build(
            GogenieDynamicConfig(
                httpIndent = "api",
                enumIndent = "state",
                mountName = "attach",
                implServiceNames = setOf("svc", "repo"),
            ),
        )

        assertNotNull(profile.findSpec("api"))
        assertNotNull(profile.findSpec("svc"))
        assertNotNull(profile.findSpec("repo"))
        assertNotNull(profile.findSpec("state"))
        assertNotNull(profile.findSpec("attach"))
        assertTrue(profile.optionSpecsFor("http").map { it.key }.containsAll(listOf("method", "route", "ns")))
        assertTrue(profile.isImplAnnotation("svc"))
        assertTrue(profile.isImplAnnotation("repo"))
        assertFalse(profile.isImplAnnotation("api"))
    }
}
