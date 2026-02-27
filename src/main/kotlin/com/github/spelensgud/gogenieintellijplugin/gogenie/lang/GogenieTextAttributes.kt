package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

object GogenieTextAttributes {
    private val ANNOTATION_AUTOWIRE: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_AUTOWIRE", 0x006D77, 0x5FD7D7)
    private val ANNOTATION_HTTP: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_HTTP", 0x137333, 0x7EE787)
    private val ANNOTATION_SERVICE: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_SERVICE", 0x0B57D0, 0x4EA1FF)
    private val ANNOTATION_DAO: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_DAO", 0xB54708, 0xFF9E64)
    private val ANNOTATION_ENUM: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_ENUM", 0xA33C6B, 0xFF7AB2)
    private val ANNOTATION_MOUNT: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_MOUNT", 0x006A64, 0x4DD0E1)
    private val ANNOTATION_RULE: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_RULE", 0xB3261E, 0xFF6B6B)
    private val ANNOTATION_SWAGGER: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_SWAGGER", 0x8A5A00, 0xEBCB8B)
    private val ANNOTATION_GRPC: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_GRPC", 0x7B3FE4, 0xC792EA)

    val ANNOTATION_OPTION: TextAttributesKey = colorKey("GOGENIE.ANNOTATION_OPTION", 0x5A4A00, 0xF2CC60)

    private val swaggerNameSet = setOf(
        "title", "version", "description", "basepath",
        "summary", "tags", "accept", "produce", "param",
        "success", "failure", "router",
    )

    private val dynamicKeys = ConcurrentHashMap<String, TextAttributesKey>()

    fun annotationNameByName(annotationName: String): TextAttributesKey {
        val name = annotationName.lowercase()
        return when {
            name == "service" -> ANNOTATION_SERVICE
            name == "dao" -> ANNOTATION_DAO
            name == "grpc" || name == "grpc_server" -> ANNOTATION_GRPC
            name == "http" || name.startsWith("http.") || name.startsWith("http:") -> ANNOTATION_HTTP
            name == "autowire" || name.startsWith("autowire.") -> ANNOTATION_AUTOWIRE
            name == "rule" || name == "rule-hash" || name.startsWith("rule.") -> ANNOTATION_RULE
            name == "enum" -> ANNOTATION_ENUM
            name == "mount" -> ANNOTATION_MOUNT
            swaggerNameSet.contains(name) -> ANNOTATION_SWAGGER
            else -> dynamicKey(name)
        }
    }

    private fun dynamicKey(name: String): TextAttributesKey {
        return dynamicKeys.computeIfAbsent(name) { rawName ->
            val hash = rawName.hashCode()
            val hue = Math.floorMod(hash, 360) / 360f
            val satSeed = Math.floorMod(hash ushr 8, 20)
            val briSeed = Math.floorMod(hash ushr 16, 16)

            val lightColor = Color.getHSBColor(
                hue,
                0.70f + satSeed / 100f,
                0.45f + briSeed / 100f,
            )
            val darkColor = Color.getHSBColor(
                hue,
                0.60f + satSeed / 125f,
                0.78f + briSeed / 100f,
            )

            val suffix = sanitizeForKey(rawName)
            val unique = Math.floorMod(hash, 10_000)
            colorKey("GOGENIE.ANNOTATION.DYNAMIC_${suffix}_$unique", lightColor, darkColor)
        }
    }

    private fun sanitizeForKey(name: String): String {
        return name.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_').ifBlank { "TAG" }
    }

    private fun colorKey(name: String, lightRgb: Int, darkRgb: Int): TextAttributesKey {
        return colorKey(name, Color(lightRgb), Color(darkRgb))
    }

    private fun colorKey(name: String, light: Color, dark: Color): TextAttributesKey {
        val attributes = TextAttributes().apply {
            foregroundColor = JBColor(light, dark)
        }
        return TextAttributesKey.createTextAttributesKey(name, attributes)
    }
}
