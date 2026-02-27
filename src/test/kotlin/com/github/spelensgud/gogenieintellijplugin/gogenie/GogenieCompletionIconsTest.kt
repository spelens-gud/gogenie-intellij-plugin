package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieCompletionIcons
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class GogenieCompletionIconsTest {
    @Test
    fun `should cache icon for same annotation`() {
        val first = GogenieCompletionIcons.annotation("service")
        val second = GogenieCompletionIcons.annotation("service")

        assertSame(first, second)
    }

    @Test
    fun `should use different icons for different annotations`() {
        val service = GogenieCompletionIcons.annotation("service")
        val grpc = GogenieCompletionIcons.annotation("grpc")

        assertNotSame(service, grpc)
    }
}

