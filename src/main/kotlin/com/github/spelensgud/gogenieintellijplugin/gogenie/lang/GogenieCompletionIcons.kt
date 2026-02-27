package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

object GogenieCompletionIcons {
    private const val BADGE_SIZE = 14
    private val annotationCache = ConcurrentHashMap<String, Icon>()
    private val optionCache = ConcurrentHashMap<String, Icon>()
    private val commandCache = ConcurrentHashMap<String, Icon>()

    fun annotation(annotationName: String): Icon {
        val normalized = annotationName.lowercase()
        return annotationCache.computeIfAbsent(normalized) {
            val color = GogenieTextAttributes.annotationNameByName(normalized)
                .defaultAttributes
                .foregroundColor
                ?: JBColor(Color(0x0B57D0), Color(0x4EA1FF))
            BadgeIcon(color, badgeLetter(normalized), BADGE_SIZE)
        }
    }

    fun option(annotationName: String): Icon {
        val normalized = annotationName.lowercase()
        return optionCache.computeIfAbsent(normalized) {
            val base = GogenieTextAttributes.annotationNameByName(normalized)
                .defaultAttributes
                .foregroundColor
                ?: JBColor(Color(0x5A4A00), Color(0xF2CC60))
            BadgeIcon(soften(base), "=", BADGE_SIZE)
        }
    }

    fun command(annotationName: String, commandLabel: String): Icon {
        val normalizedAnnotation = annotationName.lowercase()
        val normalizedCommand = commandLabel.trim().lowercase()
        val cacheKey = "$normalizedAnnotation|$normalizedCommand"
        return commandCache.computeIfAbsent(cacheKey) {
            val color = GogenieTextAttributes.annotationNameByName(normalizedAnnotation)
                .defaultAttributes
                .foregroundColor
                ?: JBColor(Color(0x0B57D0), Color(0x4EA1FF))
            BadgeIcon(color, commandBadge(normalizedCommand), BADGE_SIZE)
        }
    }

    private fun badgeLetter(annotationName: String): String {
        val head = annotationName.firstOrNull { it.isLetterOrDigit() } ?: '@'
        return head.uppercaseChar().toString()
    }

    private fun commandBadge(commandLabel: String): String {
        val tail = commandLabel.substringAfterLast(' ', commandLabel).trim()
        val head = tail.firstOrNull { it.isLetterOrDigit() } ?: '@'
        return head.uppercaseChar().toString()
    }

    private fun soften(color: Color): Color {
        val r = color.red + ((255 - color.red) * 0.25f).toInt()
        val g = color.green + ((255 - color.green) * 0.25f).toInt()
        val b = color.blue + ((255 - color.blue) * 0.25f).toInt()
        return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private class BadgeIcon(
        private val fill: Color,
        private val letter: String,
        private val size: Int,
    ) : Icon {
        override fun getIconWidth(): Int = size

        override fun getIconHeight(): Int = size

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val drawSize = size - 1
                val base = normalize(fill)
                val ring = ringColor(base)
                val bg = fillColor(base)

                // 半透明底色，避免过重的纯色块。
                g2.color = bg
                g2.fillOval(x + 1, y + 1, drawSize - 2, drawSize - 2)

                // 浅色透明外圈，风格对齐 IDE 左侧圆形标记。
                g2.color = ring
                g2.stroke = BasicStroke(1.15f)
                g2.drawOval(x + 1, y + 1, drawSize - 2, drawSize - 2)

                g2.font = Font(
                    Font.SANS_SERIF,
                    Font.PLAIN,
                    when {
                        size >= 14 -> 7
                        size >= 12 -> 7
                        else -> 6
                    },
                )
                val metrics = g2.fontMetrics
                val tx = x + (size - metrics.stringWidth(letter)) / 2
                val ty = y + ((size - metrics.height) / 2) + metrics.ascent
                g2.color = textColor(base)
                g2.drawString(letter, tx, ty)
            } finally {
                g2.dispose()
            }
        }

        private fun normalize(color: Color): Color {
            return Color(color.red, color.green, color.blue)
        }

        private fun ringColor(color: Color): Color {
            val lighten = 0.45f
            val r = color.red + ((255 - color.red) * lighten).toInt()
            val g = color.green + ((255 - color.green) * lighten).toInt()
            val b = color.blue + ((255 - color.blue) * lighten).toInt()
            return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255), 195)
        }

        private fun fillColor(color: Color): Color {
            return Color(color.red, color.green, color.blue, 56)
        }

        private fun textColor(@Suppress("UNUSED_PARAMETER") color: Color): Color {
            // 字母颜色固定，避免不同注解颜色导致字母色不一致。
            return Color(0xEC, 0xF4, 0xFF, 178)
        }

        private fun luminance(color: Color): Float {
            return (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) / 255f
        }
    }
}
