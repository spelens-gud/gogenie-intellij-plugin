package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

object GogenieHttpRouteLinkResolver {
    private val annotationRegex =
        Regex("""@([A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z0-9_.-]+)?)(\(([^)]*)\))?""")
    private val interfaceRegex = Regex("""(?m)^\s*type\s+([A-Za-z_][A-Za-z0-9_]*)\s+interface\s*\{""")
    private val methodRegex = Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*\(""")

    data class HttpRouteAnchor(
        val annotationName: String,
        val method: String?,
        val routeValue: String,
        val start: Int,
        val end: Int,
    )

    data class HttpRouteContext(
        val annotationName: String,
        val method: String?,
        val routeValue: String,
        val fullRoute: String,
        val group: String,
        val filename: String,
        val handler: String,
    )

    fun collectCommentRouteAnchors(commentText: String, profile: GogenieProfile): List<HttpRouteAnchor> {
        val anchors = mutableListOf<HttpRouteAnchor>()
        for (match in annotationRegex.findAll(commentText)) {
            val annotationName = match.groupValues[1]
            val spec = profile.findSpec(annotationName) ?: continue
            if (!spec.commandSource.equals("http", ignoreCase = true)) {
                continue
            }
            val rawArgs = match.groups[3]?.value ?: continue
            val rawStart = match.groups[3]?.range?.first ?: continue
            val parsed = parseArguments(rawArgs, rawStart)

            val routeArg = resolveRouteArgument(annotationName, parsed) ?: continue
            val routeValue = routeArg.value.trim().trim('"', '\'')
            if (routeValue.isBlank()) {
                continue
            }

            val method = resolveHttpMethod(annotationName, parsed)
            anchors += HttpRouteAnchor(
                annotationName = annotationName,
                method = method,
                routeValue = routeValue,
                start = routeArg.start,
                end = routeArg.end,
            )
        }
        return anchors
    }

    fun resolveRouteContext(
        fileText: String,
        commentStartOffset: Int,
        commentEndOffset: Int,
        anchor: HttpRouteAnchor,
        profile: GogenieProfile,
    ): HttpRouteContext? {
        val interfaceBlock = findInterfaceAtOffset(fileText, commentStartOffset) ?: return null
        val service = findServiceContextBeforeInterface(
            fileText = fileText,
            interfaceStartOffset = interfaceBlock.start,
            interfaceName = interfaceBlock.interfaceName,
            profile = profile,
        ) ?: return null

        val handler = findFollowingHandlerName(
            fileText = fileText,
            commentEndOffset = commentEndOffset,
            interfaceEndOffset = interfaceBlock.end,
        ) ?: return null

        val fullRoute = joinRoute(service.groupRoute, anchor.routeValue)
        if (fullRoute.isBlank()) {
            return null
        }

        return HttpRouteContext(
            annotationName = anchor.annotationName,
            method = anchor.method,
            routeValue = anchor.routeValue,
            fullRoute = fullRoute,
            group = service.group,
            filename = service.filename,
            handler = handler,
        )
    }

    private fun resolveRouteArgument(annotationName: String, args: List<ParsedArgument>): ParsedArgument? {
        val annotationLower = annotationName.lowercase()
        val routeByKey = args.firstOrNull { it.key.equals("route", ignoreCase = true) }
        if (routeByKey != null) {
            return routeByKey
        }
        if (annotationLower.startsWith("http.")) {
            return args.firstOrNull { it.key == null }
        }
        return args.firstOrNull { it.key == null }
    }

    private fun resolveHttpMethod(annotationName: String, args: List<ParsedArgument>): String? {
        val byKey = args.firstOrNull { it.key.equals("method", ignoreCase = true) }?.value
        if (!byKey.isNullOrBlank()) {
            return byKey.lowercase()
        }
        val lower = annotationName.lowercase()
        if (lower.startsWith("http.")) {
            return lower.substringAfter("http.", missingDelimiterValue = "").ifBlank { null }
        }
        return null
    }

    private fun findInterfaceAtOffset(fileText: String, offset: Int): InterfaceBlock? {
        var target: InterfaceBlock? = null
        for (match in interfaceRegex.findAll(fileText)) {
            val openBrace = fileText.indexOf('{', match.range.first)
            if (openBrace < 0) {
                continue
            }
            val closeBrace = findMatchingBrace(fileText, openBrace)
            if (closeBrace <= openBrace) {
                continue
            }
            if (offset in (openBrace + 1) until closeBrace) {
                target = InterfaceBlock(
                    interfaceName = match.groupValues[1],
                    start = match.range.first,
                    end = closeBrace,
                )
            }
        }
        return target
    }

    private fun findServiceContextBeforeInterface(
        fileText: String,
        interfaceStartOffset: Int,
        interfaceName: String,
        profile: GogenieProfile,
    ): ServiceContext? {
        val prefix = fileText.substring(0, interfaceStartOffset)
        var target: ServiceContext? = null
        for (match in annotationRegex.findAll(prefix)) {
            val annotationName = match.groupValues[1]
            val spec = profile.findSpec(annotationName) ?: continue
            if (!spec.commandSource.equals("impl/http", ignoreCase = true)) {
                continue
            }

            val parsed = match.groups[3]?.let {
                parseArguments(
                    raw = it.value,
                    rawStart = it.range.first,
                )
            }.orEmpty()
            val positional = parsed.firstOrNull { it.key == null }?.value?.trim()?.trim('"', '\'')
            val optionMap = parsed
                .filter { !it.key.isNullOrBlank() }
                .associateBy(
                    keySelector = { it.key!!.lowercase() },
                    valueTransform = { it.value.trim().trim('"', '\'') },
                )

            val serviceName = positional?.takeIf { it.isNotBlank() } ?: interfaceName
            val group = optionMap["group"]?.takeIf { it.isNotBlank() } ?: serviceName
            val groupRoute = optionMap["route"]?.takeIf { it.isNotBlank() } ?: serviceName
            val filename = optionMap["filename"]?.takeIf { it.isNotBlank() } ?: serviceName
            target = ServiceContext(
                group = group.trim('/'),
                groupRoute = groupRoute,
                filename = filename,
            )
        }
        return target
    }

    private fun findFollowingHandlerName(
        fileText: String,
        commentEndOffset: Int,
        interfaceEndOffset: Int,
    ): String? {
        if (commentEndOffset < 0 || commentEndOffset > fileText.length) {
            return null
        }
        val searchEnd = interfaceEndOffset.coerceIn(commentEndOffset, fileText.length)
        val suffix = fileText.substring(commentEndOffset, searchEnd)
        val methodMatch = methodRegex.find(suffix) ?: return null
        return methodMatch.groupValues[1].ifBlank { null }
    }

    private fun parseArguments(raw: String, rawStart: Int): List<ParsedArgument> {
        val ret = mutableListOf<ParsedArgument>()
        val segments = splitTopLevel(raw)
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

    private fun splitTopLevel(raw: String): List<Pair<Int, Int>> {
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
                ',' -> {
                    if (!inSingle && !inDouble) {
                        ret += start to index
                        start = index + 1
                    }
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

    private fun joinRoute(groupRoute: String, baseRoute: String): String {
        val base = baseRoute.trim().trim('"', '\'')
        if (base.isBlank()) {
            return ""
        }
        val group = groupRoute.trim().trim('"', '\'')
        val groupPart = group.trim('/')
        val basePart = base.trim('/')
        val joined = listOf(groupPart, basePart)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .trim('/')
        if (joined.isBlank()) {
            return ""
        }
        return if (base.endsWith("/") && !joined.endsWith("/")) "$joined/" else joined
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

    private data class ServiceContext(
        val group: String,
        val groupRoute: String,
        val filename: String,
    )

    private data class InterfaceBlock(
        val interfaceName: String,
        val start: Int,
        val end: Int,
    )
}
