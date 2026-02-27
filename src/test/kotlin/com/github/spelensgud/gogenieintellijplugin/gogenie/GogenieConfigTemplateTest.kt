package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.ui.GogenieConfigTemplate
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieConfigTemplateTest {
    @Test
    fun `full template should contain global and commands sections`() {
        val yaml = GogenieConfigTemplate.fullTemplate()
        assertTrue(yaml.contains("global:"))
        assertTrue(yaml.contains("commands:"))
        assertTrue(yaml.contains("db2struct:"))
        assertTrue(yaml.contains("autowire:"))
        assertTrue(yaml.contains("llm:"))
    }
}
