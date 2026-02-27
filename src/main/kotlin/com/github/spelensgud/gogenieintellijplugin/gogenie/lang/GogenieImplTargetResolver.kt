package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

object GogenieImplTargetResolver {
    private val interfaceDeclRegex = Regex("""^\s*(?:(?://[^\n]*\n)\s*)*type\s+([A-Za-z_][A-Za-z0-9_]*)\s+interface\b""")

    fun resolveImplAnnotation(commentText: String, profile: GogenieProfile): String? {
        val matches = GogenieAnnotationTextAnalyzer.extractAnnotations(commentText, profile)
        return matches.firstOrNull { it.recognized && profile.isImplAnnotation(it.name) }?.name
    }

    fun resolveFollowingInterfaceName(fileText: String, commentEndOffset: Int): String? {
        if (commentEndOffset !in 0..fileText.length) {
            return null
        }
        val suffix = fileText.substring(commentEndOffset)
        val match = interfaceDeclRegex.find(suffix) ?: return null
        return match.groupValues[1].ifBlank { null }
    }
}
