package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieTextAttributes
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.awt.Color

class GogenieTextAttributesTest {
    @Test
    fun `should use different keys for service and dao annotations`() {
        val serviceKey = GogenieTextAttributes.annotationNameByName("service")
        val daoKey = GogenieTextAttributes.annotationNameByName("dao")

        assertNotEquals(serviceKey.externalName, daoKey.externalName)
    }

    @Test
    fun `should map same annotation name to stable key`() {
        val first = GogenieTextAttributes.annotationNameByName("custom_api")
        val second = GogenieTextAttributes.annotationNameByName("custom_api")

        assertEquals(first.externalName, second.externalName)
    }

    @Test
    fun `should assign explicit non white colors for service and grpc`() {
        val serviceColor = GogenieTextAttributes.annotationNameByName("service").defaultAttributes.foregroundColor
        val grpcColor = GogenieTextAttributes.annotationNameByName("grpc").defaultAttributes.foregroundColor

        assertNotNull(serviceColor)
        assertNotNull(grpcColor)
        assertNotEquals(serviceColor!!.rgb, grpcColor!!.rgb)
        assertNotEquals(Color.WHITE.rgb, serviceColor.rgb)
        assertNotEquals(Color.WHITE.rgb, grpcColor.rgb)
    }
}
