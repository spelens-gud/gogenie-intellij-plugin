package com.github.spelensgud.gogenieintellijplugin.gogenie.config

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

object GogenieConfigParser {
    fun parse(path: Path): GogenieDynamicConfig {
        return try {
            val raw = Files.readString(path)
            val entries = when (path.extension.lowercase()) {
                "yaml", "yml" -> parseYaml(raw)
                "toml" -> parseToml(raw)
                else -> parseYaml(raw)
            }
            buildDynamicConfig(entries, path)
        } catch (e: Exception) {
            GogenieDynamicConfig.defaults(path).copy(parseError = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun buildDynamicConfig(entries: Map<String, List<String>>, path: Path): GogenieDynamicConfig {
        fun firstOrNull(key: String): String? = entries[key]?.firstOrNull()?.takeIf { it.isNotBlank() }
        fun allNonBlank(key: String): Set<String> =
            entries[key].orEmpty().filter { it.isNotBlank() }.toSet()

        val implServices = allNonBlank("commands.impl.indents.service")

        return GogenieDynamicConfig(
            httpIndent = firstOrNull("commands.http.indent") ?: GogenieDynamicConfig.DEFAULT_HTTP_INDENT,
            enumIndent = firstOrNull("commands.enum.indent") ?: GogenieDynamicConfig.DEFAULT_ENUM_INDENT,
            enumOutputPath = firstOrNull("commands.enum.output_path") ?: GogenieDynamicConfig.DEFAULT_ENUM_OUTPUT_PATH,
            mountName = firstOrNull("commands.mount.name") ?: GogenieDynamicConfig.DEFAULT_MOUNT_NAME,
            implServiceNames = implServices.ifEmpty { GogenieDynamicConfig.DEFAULT_IMPL_SERVICE_NAMES },
            configPath = path,
        )
    }

    private fun parseYaml(content: String): Map<String, List<String>> {
        val entries = mutableMapOf<String, MutableList<String>>()
        val stack = ArrayDeque<Pair<Int, String>>()

        for (rawLine in content.lineSequence()) {
            val line = stripComment(rawLine)
            if (line.isBlank()) {
                continue
            }

            val indent = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val trimmed = line.trim()

            while (stack.isNotEmpty() && indent <= stack.last().first) {
                stack.removeLast()
            }

            if (trimmed.startsWith("- ")) {
                val listContent = trimmed.removePrefix("- ").trim()
                val listPair = splitKeyValue(listContent) ?: continue
                val parentPath = stack.joinToString(".") { it.second }
                val fullPath = if (parentPath.isEmpty()) listPair.first else "$parentPath.${listPair.first}"
                addEntry(entries, fullPath, listPair.second)
                stack.addLast(indent to listPair.first)
                continue
            }

            val pair = splitKeyValue(trimmed) ?: continue
            val parentPath = stack.joinToString(".") { it.second }
            val fullPath = if (parentPath.isEmpty()) pair.first else "$parentPath.${pair.first}"
            addEntry(entries, fullPath, pair.second)
            stack.addLast(indent to pair.first)
        }

        return entries
    }

    private fun parseToml(content: String): Map<String, List<String>> {
        val entries = mutableMapOf<String, MutableList<String>>()
        var section = ""

        for (rawLine in content.lineSequence()) {
            val line = stripComment(rawLine).trim()
            if (line.isBlank()) {
                continue
            }

            if (line.startsWith("[[") && line.endsWith("]]")) {
                section = line.removePrefix("[[").removeSuffix("]]").trim()
                continue
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.removePrefix("[").removeSuffix("]").trim()
                continue
            }

            val eqIndex = line.indexOf('=')
            if (eqIndex <= 0) {
                continue
            }
            val key = line.substring(0, eqIndex).trim()
            val value = normalizeValue(line.substring(eqIndex + 1))
            val fullPath = if (section.isBlank()) key else "$section.$key"
            addEntry(entries, fullPath, value)
        }

        return entries
    }

    private fun splitKeyValue(line: String): Pair<String, String?>? {
        val colonIndex = line.indexOf(':')
        if (colonIndex <= 0) {
            return null
        }
        val key = line.substring(0, colonIndex).trim()
        val value = line.substring(colonIndex + 1).trim().ifBlank { null }?.let(::normalizeValue)
        if (key.isBlank()) {
            return null
        }
        return key to value
    }

    private fun addEntry(entries: MutableMap<String, MutableList<String>>, path: String, value: String?) {
        if (value == null || path.isBlank()) {
            return
        }
        entries.getOrPut(path) { mutableListOf() }.add(value)
    }

    private fun normalizeValue(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            if (trimmed.startsWith('"') && trimmed.endsWith('"')) {
                return trimmed.substring(1, trimmed.length - 1).trim()
            }
            if (trimmed.startsWith('\'') && trimmed.endsWith('\'')) {
                return trimmed.substring(1, trimmed.length - 1).trim()
            }
        }
        return trimmed
    }

    private fun stripComment(rawLine: String): String {
        var inSingleQuote = false
        var inDoubleQuote = false
        val sb = StringBuilder(rawLine.length)

        for (c in rawLine) {
            when (c) {
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '#' -> if (!inSingleQuote && !inDoubleQuote) break
            }
            sb.append(c)
        }

        return sb.toString()
    }
}
