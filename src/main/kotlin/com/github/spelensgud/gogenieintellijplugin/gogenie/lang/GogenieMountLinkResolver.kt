package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

object GogenieMountLinkResolver {
    private val annotationRegex =
        Regex("""@([A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z0-9_.-]+)?)(\(([^)]*)\))?""")
    private val structHeadRegex = Regex("""(?m)^\s*type\s+([A-Za-z_][A-Za-z0-9_]*)\s+struct\s*\{""")
    private val identRegex = Regex("""^[A-Za-z_][A-Za-z0-9_]*$""")
    private val lineFieldRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\b""")

    data class MountBinding(
        val alias: String,
        val structName: String,
        val structNameOffset: Int,
        val fieldOffsets: Map<String, Int>,
    )

    data class MountValueAnchor(
        val annotationName: String,
        val valueName: String,
        val start: Int,
        val end: Int,
    )

    fun collectCommentValueAnchors(commentText: String, profile: GogenieProfile): List<MountValueAnchor> {
        val anchors = mutableListOf<MountValueAnchor>()
        for (match in annotationRegex.findAll(commentText)) {
            val annotationName = match.groupValues[1]
            val spec = profile.findSpec(annotationName) ?: continue
            val source = spec.commandSource.lowercase()
            if (!source.startsWith("mount/")) {
                continue
            }
            val aliasName = source.substringAfter("mount/").ifBlank { annotationName }.lowercase()
            val argGroup = match.groups[3] ?: continue
            val args = parseArguments(argGroup.value, argGroup.range.first)
            val keyMatched = args.filter { it.key?.lowercase() == aliasName }
            val candidates = if (keyMatched.isNotEmpty()) keyMatched else args
            for (arg in candidates) {
                val value = arg.value.trim().trim('"', '\'')
                if (!identRegex.matches(value)) {
                    continue
                }
                anchors += MountValueAnchor(
                    annotationName = annotationName,
                    valueName = value,
                    start = arg.start,
                    end = arg.end,
                )
            }
        }
        return anchors
    }

    fun collectMountBindingsFromFile(fileText: String, mountRoots: Set<String>): List<MountBinding> {
        if (mountRoots.isEmpty()) {
            return emptyList()
        }
        val ret = mutableListOf<MountBinding>()

        for (match in annotationRegex.findAll(fileText)) {
            val annotationName = match.groupValues[1].lowercase()
            if (!mountRoots.contains(annotationName)) {
                continue
            }

            val argGroup = match.groups[3] ?: continue
            val aliases = parseMountAliases(argGroup.value)
            if (aliases.isEmpty()) {
                continue
            }

            val annotationEnd = match.range.last + 1
            val structHead = structHeadRegex.find(fileText, annotationEnd) ?: continue
            val between = fileText.substring(annotationEnd, structHead.range.first)
            if (!isTriviaOnly(between)) {
                continue
            }
            val structNameGroup = structHead.groups[1] ?: continue
            val structName = structNameGroup.value
            val structNameOffset = structNameGroup.range.first

            val openBrace = fileText.indexOf('{', structHead.range.first)
            if (openBrace < 0) {
                continue
            }
            val closeBrace = findMatchingBrace(fileText, openBrace)
            if (closeBrace <= openBrace) {
                continue
            }
            val fieldOffsets = collectStructFieldOffsets(fileText, openBrace + 1, closeBrace)

            for (alias in aliases) {
                ret += MountBinding(
                    alias = alias,
                    structName = structName,
                    structNameOffset = structNameOffset,
                    fieldOffsets = fieldOffsets,
                )
            }
        }

        return ret
    }

    private fun parseMountAliases(raw: String): Set<String> {
        val aliases = linkedSetOf<String>()
        for (segment in splitTopLevel(raw)) {
            val candidate = parseAliasFromSegment(segment) ?: continue
            aliases += candidate
        }
        return aliases
    }

    private fun parseAliasFromSegment(segmentRaw: String): String? {
        val segment = segmentRaw.trim()
        if (segment.isBlank()) {
            return null
        }
        val eq = findTopLevelEquals(segment)
        val candidate = if (eq >= 0) {
            segment.substring(0, eq).trim()
        } else {
            segment.trim().trim('"', '\'')
        }
        if (!identRegex.matches(candidate)) {
            return null
        }
        return candidate
    }

    private fun parseArguments(raw: String, rawStart: Int): List<ParsedArgument> {
        val ret = mutableListOf<ParsedArgument>()
        val segments = splitTopLevelWithRanges(raw)
        for ((segStart, segEnd) in segments) {
            if (segEnd <= segStart) {
                continue
            }
            val absSegStart = rawStart + segStart
            val segmentText = raw.substring(segStart, segEnd)
            val leftTrim = segmentText.indexOfFirst { !it.isWhitespace() }
            if (leftTrim < 0) {
                continue
            }
            val rightTrimExclusive = segmentText.indexOfLast { !it.isWhitespace() } + 1
            if (rightTrimExclusive <= leftTrim) {
                continue
            }

            val trimStart = absSegStart + leftTrim
            val trimEnd = absSegStart + rightTrimExclusive
            val trimmed = raw.substring(trimStart - rawStart, trimEnd - rawStart)
            val eq = findTopLevelEquals(trimmed)

            val key: String?
            var valueStart: Int
            var valueEnd: Int

            if (eq >= 0) {
                val keyRaw = trimmed.substring(0, eq).trim()
                key = keyRaw.ifBlank { null }
                val valueRaw = trimmed.substring(eq + 1)
                val valueLeftTrim = valueRaw.indexOfFirst { !it.isWhitespace() }
                if (valueLeftTrim < 0) {
                    continue
                }
                val valueRightTrim = valueRaw.indexOfLast { !it.isWhitespace() } + 1
                valueStart = trimStart + eq + 1 + valueLeftTrim
                valueEnd = trimStart + eq + 1 + valueRightTrim
            } else {
                key = null
                valueStart = trimStart
                valueEnd = trimEnd
            }

            val bounds = unquoteBounds(raw, valueStart - rawStart, valueEnd - rawStart)
            valueStart = rawStart + bounds.first
            valueEnd = rawStart + bounds.second
            if (valueEnd <= valueStart) {
                continue
            }
            val value = raw.substring(valueStart - rawStart, valueEnd - rawStart)
            ret += ParsedArgument(
                key = key,
                value = value,
                start = valueStart,
                end = valueEnd,
            )
        }
        return ret
    }

    private fun collectStructFieldOffsets(fileText: String, bodyStart: Int, bodyEnd: Int): Map<String, Int> {
        val fieldOffsets = linkedMapOf<String, Int>()
        val body = fileText.substring(bodyStart, bodyEnd)
        var offset = bodyStart
        for (line in body.lineSequence()) {
            val lineNoComment = line.substringBefore("//")
            if (lineNoComment.isBlank()) {
                offset += line.length + 1
                continue
            }
            val match = lineFieldRegex.find(lineNoComment)
            if (match != null) {
                val name = match.groupValues[1]
                val localOffset = match.groups[1]?.range?.first ?: -1
                if (localOffset >= 0 && identRegex.matches(name) && !isGoKeyword(name)) {
                    fieldOffsets.putIfAbsent(name, offset + localOffset)
                }
            }
            offset += line.length + 1
        }
        return fieldOffsets
    }

    private fun isGoKeyword(name: String): Boolean {
        return name in setOf(
            "type", "func", "var", "const", "map", "chan", "interface", "struct",
            "return", "if", "for", "switch", "case", "default",
        )
    }

    private fun splitTopLevel(raw: String): List<String> {
        return splitTopLevelWithRanges(raw).map { (start, end) -> raw.substring(start, end) }
    }

    private fun splitTopLevelWithRanges(raw: String): List<Pair<Int, Int>> {
        val ret = mutableListOf<Pair<Int, Int>>()
        var start = 0
        var inSingle = false
        var inDouble = false
        var escaped = false
        for (index in raw.indices) {
            val ch = raw[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && (inSingle || inDouble)) {
                escaped = true
                continue
            }
            when (ch) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                ',' -> if (!inSingle && !inDouble) {
                    ret += start to index
                    start = index + 1
                }
            }
        }
        ret += start to raw.length
        return ret
    }

    private fun findTopLevelEquals(text: String): Int {
        var inSingle = false
        var inDouble = false
        var escaped = false
        for (index in text.indices) {
            val ch = text[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && (inSingle || inDouble)) {
                escaped = true
                continue
            }
            when (ch) {
                '\'' -> if (!inDouble) inSingle = !inSingle
                '"' -> if (!inSingle) inDouble = !inDouble
                '=' -> if (!inSingle && !inDouble) return index
            }
        }
        return -1
    }

    private fun unquoteBounds(text: String, start: Int, end: Int): Pair<Int, Int> {
        if (end - start < 2) {
            return start to end
        }
        val startCh = text.getOrNull(start) ?: return start to end
        val endCh = text.getOrNull(end - 1) ?: return start to end
        if ((startCh == '"' || startCh == '\'') && startCh == endCh) {
            return start + 1 to end - 1
        }
        return start to end
    }

    private fun isTriviaOnly(text: String): Boolean {
        if (text.isBlank()) {
            return true
        }
        return text.lineSequence().all { line ->
            val trimmed = line.trim()
            trimmed.isEmpty() || trimmed.startsWith("//")
        }
    }

    private fun findMatchingBrace(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
        }
        return -1
    }

    private data class ParsedArgument(
        val key: String?,
        val value: String,
        val start: Int,
        val end: Int,
    )
}
