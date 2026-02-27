package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

object GogenieEnumLinkResolver {
    private val annotationRegex = Regex("""@([A-Za-z][A-Za-z0-9_.:-]*)\(([^)]*)\)""")
    private val constHeadRegex = Regex("""(?m)^\s*const\s*\(""")
    private val constNameRegex = Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\b(?:\s+[A-Za-z_][A-Za-z0-9_]*)?\s*=""")
    private val identRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

    data class EnumSourceTarget(
        val enumName: String,
        val constName: String? = null,
    )

    data class EnumCommentAnchor(
        val annotationName: String,
        val enumName: String,
        val start: Int,
        val end: Int,
    )

    data class EnumSemanticRange(
        val annotationName: String,
        val enumName: String,
        val constName: String?,
        val start: Int,
        val end: Int,
        val kind: Kind,
    ) {
        enum class Kind {
            ENUM_ARGUMENT,
            CONST_NAME,
        }
    }

    fun resolveEnumNameFromComment(commentText: String, profile: GogenieProfile): String? {
        return collectCommentEnumAnchors(commentText, profile).firstOrNull()?.enumName
    }

    fun collectCommentEnumAnchors(commentText: String, profile: GogenieProfile): List<EnumCommentAnchor> {
        return collectEnumArguments(commentText, profile).map {
            EnumCommentAnchor(
                annotationName = it.annotationName,
                enumName = it.enumName,
                start = it.start,
                end = it.end,
            )
        }
    }

    fun collectSemanticRanges(fileText: String, profile: GogenieProfile): List<EnumSemanticRange> {
        val ranges = mutableListOf<EnumSemanticRange>()
        for (argument in collectEnumArguments(fileText, profile)) {
            ranges += EnumSemanticRange(
                annotationName = argument.annotationName,
                enumName = argument.enumName,
                constName = null,
                start = argument.start,
                end = argument.end,
                kind = EnumSemanticRange.Kind.ENUM_ARGUMENT,
            )
        }

        val blocks = collectEnumConstBlocks(fileText, profile)
        for (block in blocks) {
            for (item in block.constItems) {
                ranges += EnumSemanticRange(
                    annotationName = block.annotationName,
                    enumName = block.enumName,
                    constName = item.name,
                    start = item.start,
                    end = item.end,
                    kind = EnumSemanticRange.Kind.CONST_NAME,
                )
            }
        }
        return ranges
    }

    fun semanticRangeAtOffset(fileText: String, offset: Int, profile: GogenieProfile): EnumSemanticRange? {
        if (offset < 0 || offset > fileText.length) {
            return null
        }
        return collectSemanticRanges(fileText, profile).firstOrNull { range ->
            offset in range.start until range.end || offset == range.end
        }
    }

    fun resolveAtOffset(fileText: String, offset: Int, profile: GogenieProfile): EnumSourceTarget? {
        if (offset !in 0..fileText.length) {
            return null
        }

        val byAnnotation = resolveAnnotationArgumentAtOffset(fileText, offset, profile)
        if (byAnnotation != null) {
            return byAnnotation
        }

        return resolveConstNameAtOffset(fileText, offset, profile)
    }

    private fun resolveAnnotationArgumentAtOffset(
        fileText: String,
        offset: Int,
        profile: GogenieProfile,
    ): EnumSourceTarget? {
        for (argument in collectEnumArguments(fileText, profile)) {
            if (!isOffsetInside(argument.start, argument.end, offset)) {
                continue
            }
            return EnumSourceTarget(enumName = argument.enumName)
        }
        return null
    }

    private fun resolveConstNameAtOffset(fileText: String, offset: Int, profile: GogenieProfile): EnumSourceTarget? {
        val identifier = identifierAtOffset(fileText, offset) ?: return null
        val blocks = collectEnumConstBlocks(fileText, profile)
        for (block in blocks) {
            for (item in block.constItems) {
                if (item.name == identifier.text && identifier.start in item.start until item.end) {
                    return EnumSourceTarget(enumName = block.enumName, constName = item.name)
                }
            }
        }
        return null
    }

    private fun collectEnumConstBlocks(fileText: String, profile: GogenieProfile): List<EnumConstBlock> {
        val blocks = mutableListOf<EnumConstBlock>()
        for (annotation in annotationRegex.findAll(fileText)) {
            val annotationName = annotation.groupValues[1]
            if (!isEnumAnnotation(annotationName, profile)) {
                continue
            }
            val argGroup = annotation.groups[2] ?: continue
            val enumArgument = parseEnumArgument(argGroup.value, argGroup.range.first) ?: continue
            val enumName = enumArgument.enumName
            val annotationEnd = annotation.range.last + 1

            val constHead = constHeadRegex.find(fileText, annotationEnd) ?: continue
            val between = fileText.substring(annotationEnd, constHead.range.first)
            if (!isTriviaOnly(between)) {
                continue
            }

            val openParen = fileText.indexOf('(', constHead.range.first)
            if (openParen < 0) {
                continue
            }
            val closeParen = findMatchingParen(fileText, openParen)
            if (closeParen <= openParen) {
                continue
            }

            val bodyStart = openParen + 1
            val body = fileText.substring(bodyStart, closeParen)
            val constItems = mutableListOf<EnumConstItem>()
            for (nameMatch in constNameRegex.findAll(body)) {
                val nameGroup = nameMatch.groups[1] ?: continue
                constItems += EnumConstItem(
                    name = nameGroup.value,
                    start = bodyStart + nameGroup.range.first,
                    end = bodyStart + nameGroup.range.last + 1,
                )
            }
            if (constItems.isNotEmpty()) {
                blocks += EnumConstBlock(
                    annotationName = annotationName,
                    enumName = enumName,
                    constItems = constItems,
                )
            }
        }
        return blocks
    }

    private fun collectEnumArguments(fileText: String, profile: GogenieProfile): List<EnumArgument> {
        val arguments = mutableListOf<EnumArgument>()
        for (match in annotationRegex.findAll(fileText)) {
            val annotationName = match.groupValues[1]
            if (!isEnumAnnotation(annotationName, profile)) {
                continue
            }
            val argGroup = match.groups[2] ?: continue
            val parsed = parseEnumArgument(argGroup.value, argGroup.range.first) ?: continue
            arguments += EnumArgument(
                annotationName = annotationName,
                enumName = parsed.enumName,
                start = parsed.start,
                end = parsed.end,
            )
        }
        return arguments
    }

    private fun identifierAtOffset(fileText: String, offset: Int): IdentifierRange? {
        val index = when {
            offset < 0 || offset > fileText.length -> return null
            offset == fileText.length -> offset - 1
            fileText[offset].isLetterOrDigit() || fileText[offset] == '_' -> offset
            offset > 0 && (fileText[offset - 1].isLetterOrDigit() || fileText[offset - 1] == '_') -> offset - 1
            else -> return null
        }

        var start = index
        var end = index + 1
        while (start > 0 && (fileText[start - 1].isLetterOrDigit() || fileText[start - 1] == '_')) {
            start--
        }
        while (end < fileText.length && (fileText[end].isLetterOrDigit() || fileText[end] == '_')) {
            end++
        }
        val text = fileText.substring(start, end)
        if (!identRegex.matches(text)) {
            return null
        }
        return IdentifierRange(text = text, start = start, end = end)
    }

    private fun isEnumAnnotation(annotationName: String, profile: GogenieProfile): Boolean {
        return profile.findSpec(annotationName)?.commandSource?.equals("enum", ignoreCase = true) == true
    }

    private fun parseEnumArgument(raw: String, groupStart: Int): ParsedEnumArgument? {
        val firstSegment = raw.substringBefore(',')
        var startInSegment = firstSegment.indexOfFirst { !it.isWhitespace() }
        if (startInSegment < 0) {
            return null
        }

        var endInSegment = firstSegment.length
        while (endInSegment > startInSegment && firstSegment[endInSegment - 1].isWhitespace()) {
            endInSegment--
        }
        if (endInSegment <= startInSegment) {
            return null
        }

        val startChar = firstSegment[startInSegment]
        val endChar = firstSegment[endInSegment - 1]
        if ((startChar == '"' && endChar == '"') || (startChar == '\'' && endChar == '\'')) {
            if (endInSegment - startInSegment <= 2) {
                return null
            }
            startInSegment++
            endInSegment--
        }

        val enumName = firstSegment.substring(startInSegment, endInSegment).trim()
        if (enumName.isBlank()) {
            return null
        }

        return ParsedEnumArgument(
            enumName = enumName,
            start = groupStart + startInSegment,
            end = groupStart + endInSegment,
        )
    }

    private fun isOffsetInside(start: Int, end: Int, offset: Int): Boolean {
        return offset in start until end || offset == end
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

    private fun findMatchingParen(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return i
                    }
                }
            }
        }
        return -1
    }

    private data class IdentifierRange(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private data class EnumConstBlock(
        val annotationName: String,
        val enumName: String,
        val constItems: List<EnumConstItem>,
    )

    private data class EnumConstItem(
        val name: String,
        val start: Int,
        val end: Int,
    )

    private data class EnumArgument(
        val annotationName: String,
        val enumName: String,
        val start: Int,
        val end: Int,
    )

    private data class ParsedEnumArgument(
        val enumName: String,
        val start: Int,
        val end: Int,
    )
}
