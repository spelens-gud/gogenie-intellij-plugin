package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

object GogenieAnnotationTextAnalyzer {
    private val optionContextRegex =
        Regex("""@([A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z0-9_.-]+)?)\(([^)]*)$""")
    private val nameContextRegex = Regex("""@([A-Za-z0-9_.:-]*)$""")
    private val annotationRegex =
        Regex("""@([A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z0-9_.-]+)?)(\(([^)]*)\))?""")
    private val optionKeyRegex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=""")

    sealed interface CompletionContext {
        data object None : CompletionContext
        data class AnnotationName(val typedPrefix: String) : CompletionContext
        data class AnnotationOption(val annotationName: String, val typedPrefix: String) : CompletionContext
    }

    data class TextSpan(val start: Int, val end: Int)

    data class OptionMatch(
        val key: String,
        val span: TextSpan,
    )

    data class AnnotationMatch(
        val name: String,
        val recognized: Boolean,
        val commandSource: String? = null,
        val nameSpan: TextSpan,
        val options: List<OptionMatch>,
    )

    fun resolveCompletionContext(commentText: String, caretOffset: Int): CompletionContext {
        if (caretOffset <= 0 || caretOffset > commentText.length) {
            return CompletionContext.None
        }

        val lineStart = commentText.lastIndexOf('\n', startIndex = caretOffset - 1).let { idx ->
            if (idx < 0) 0 else idx + 1
        }
        val beforeCaret = commentText.substring(lineStart, caretOffset)

        val optionContext = optionContextRegex.findAll(beforeCaret).lastOrNull()
        if (optionContext != null && !isLikelyEmail(beforeCaret, optionContext.range.first)) {
            val annotationName = optionContext.groupValues[1]
            val rawArgs = optionContext.groupValues[2]
            val lastSegment = rawArgs.substringAfterLast(',').trimStart()
            if (!lastSegment.contains('=')) {
                return CompletionContext.AnnotationOption(annotationName, lastSegment)
            }
        }

        val nameContext = nameContextRegex.find(beforeCaret)
        if (nameContext != null && !isLikelyEmail(beforeCaret, nameContext.range.first)) {
            return CompletionContext.AnnotationName(nameContext.groupValues[1])
        }

        return CompletionContext.None
    }

    fun extractAnnotations(commentText: String, profile: GogenieProfile): List<AnnotationMatch> {
        val matches = mutableListOf<AnnotationMatch>()

        for (match in annotationRegex.findAll(commentText)) {
            if (isLikelyEmail(commentText, match.range.first)) {
                continue
            }

            val name = match.groupValues[1]
            val spec = profile.findSpec(name)
            val recognized = spec != null
            val nameStart = match.range.first + 1
            val nameEnd = nameStart + name.length
            val optionMatches = mutableListOf<OptionMatch>()

            val rawOptions = match.groups[3]?.value.orEmpty()
            val rawOptionStart = match.groups[3]?.range?.first ?: -1
            if (rawOptionStart >= 0 && rawOptions.isNotBlank()) {
                for (option in optionKeyRegex.findAll(rawOptions)) {
                    val key = option.groupValues[1]
                    if (!recognized) {
                        continue
                    }
                    if (!spec.allowAnyOption && spec.options.isNotEmpty() && spec.options.none { it.key == key }) {
                        continue
                    }

                    val keyStart = rawOptionStart + option.range.first
                    optionMatches += OptionMatch(
                        key = key,
                        span = TextSpan(keyStart, keyStart + key.length),
                    )
                }
            }

            matches += AnnotationMatch(
                name = name,
                recognized = recognized,
                commandSource = spec?.commandSource,
                nameSpan = TextSpan(nameStart, nameEnd),
                options = optionMatches,
            )
        }

        return matches
    }

    fun isLikelyEmail(text: String, atIndex: Int): Boolean {
        if (atIndex <= 0 || atIndex >= text.length) {
            return false
        }
        val before = text[atIndex - 1]
        if (!before.isLetterOrDigit() && before !in "._%+-") {
            return false
        }
        val after = text.getOrNull(atIndex + 1) ?: return false
        return after.isLetterOrDigit()
    }
}
