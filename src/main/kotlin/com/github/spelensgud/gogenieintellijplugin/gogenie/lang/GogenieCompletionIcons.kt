package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

object GogenieCompletionIcons {
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
            BadgeIcon(color, badgeLetter(normalized), 12)
        }
    }

    fun option(annotationName: String): Icon {
        val normalized = annotationName.lowercase()
        return optionCache.computeIfAbsent(normalized) {
            val base = GogenieTextAttributes.annotationNameByName(normalized)
                .defaultAttributes
                .foregroundColor
                ?: JBColor(Color(0x5A4A00), Color(0xF2CC60))
            BadgeIcon(soften(base), "=", 10)
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
            BadgeIcon(color, commandBadge(normalizedCommand), 12)
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

                g2.color = fill
                g2.fillOval(x, y, size - 1, size - 1)

                g2.color = borderColor(fill)
                g2.drawOval(x, y, size - 1, size - 1)

                g2.font = Font(Font.SANS_SERIF, Font.BOLD, if (size >= 12) 9 else 8)
                val metrics = g2.fontMetrics
                val tx = x + (size - metrics.stringWidth(letter)) / 2
                val ty = y + ((size - metrics.height) / 2) + metrics.ascent
                g2.color = textColor(fill)
                g2.drawString(letter, tx, ty)
            } finally {
                g2.dispose()
            }
        }

        private fun borderColor(color: Color): Color {
            return if (luminance(color) > 0.7f) color.darker() else color.brighter()
        }

        private fun textColor(color: Color): Color {
            return if (luminance(color) > 0.62f) Color(0x0F172A) else Color(0xF8FAFC)
        }

        private fun luminance(color: Color): Float {
            return (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) / 255f
        }
    }
}
