package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.AnnotationSpec
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import java.util.concurrent.ConcurrentHashMap

object GogenieMountAliasCatalog {
    private val annotationRegex =
        Regex("""@([A-Za-z][A-Za-z0-9_.-]*(?::[A-Za-z0-9_.-]+)?)(\(([^)]*)\))?""")
    private val identRegex = Regex("""^[A-Za-z_][A-Za-z0-9_]*$""")
    private val fileAliasCache = ConcurrentHashMap<FileAliasCacheKey, Set<String>>()

    fun augmentProfile(profile: GogenieProfile, file: PsiFile): GogenieProfile {
        val mountRoots = mountRootNames(profile)
        if (mountRoots.isEmpty()) {
            return profile
        }

        val projectAliases = file.project.service<GogenieMountAliasService>().getAliases(profile)
        val currentFileAliases = collectFileAliases(file, mountRoots)
        return appendAliases(profile, projectAliases + currentFileAliases)
    }

    fun augmentProfile(profile: GogenieProfile, fileText: String): GogenieProfile {
        val mountRoots = mountRootNames(profile)
        if (mountRoots.isEmpty()) {
            return profile
        }
        return appendAliases(profile, collectAliasesFromText(fileText, mountRoots))
    }

    fun mountRootNames(profile: GogenieProfile): Set<String> {
        return profile.specs
            .asSequence()
            .filter { it.commandSource.equals("mount", ignoreCase = true) }
            .map { it.name.lowercase() }
            .toSet()
    }

    fun collectAliasesFromText(fileText: String, mountRoots: Set<String>): Set<String> {
        val aliases = linkedSetOf<String>()
        for (match in annotationRegex.findAll(fileText)) {
            val annotationName = match.groupValues[1].lowercase()
            if (!mountRoots.contains(annotationName)) {
                continue
            }
            val rawArgs = match.groups[3]?.value ?: continue
            for (segment in splitTopLevel(rawArgs)) {
                parseAliasFromSegment(segment)?.let { aliases += it }
            }
        }
        return aliases
    }

    private fun appendAliases(profile: GogenieProfile, aliases: Set<String>): GogenieProfile {
        if (aliases.isEmpty()) {
            return profile
        }

        val specs = profile.specs.toMutableList()
        val existing = specs.map { it.name.lowercase() }.toMutableSet()
        for (alias in aliases) {
            val aliasLower = alias.lowercase()
            if (!existing.add(aliasLower)) {
                continue
            }
            specs += AnnotationSpec(
                name = alias,
                commandSource = "mount/$alias",
                snippet = "@$alias(...)",
                allowAnyOption = true,
            )
        }
        if (specs.size == profile.specs.size) {
            return profile
        }
        return profile.copy(specs = specs)
    }

    private fun collectFileAliases(file: PsiFile, mountRoots: Set<String>): Set<String> {
        val path = file.virtualFile?.path ?: return collectAliasesFromText(file.text, mountRoots)
        val key = FileAliasCacheKey(
            filePath = path,
            fileStamp = file.modificationStamp,
            mountRootsSignature = mountRoots.sorted().joinToString(","),
        )
        fileAliasCache[key]?.let { return it }

        val aliases = collectAliasesFromText(file.text, mountRoots)
        if (fileAliasCache.size > 512) {
            fileAliasCache.clear()
        }
        fileAliasCache[key] = aliases
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

    private fun splitTopLevel(raw: String): List<String> {
        val ret = mutableListOf<String>()
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
                    ret += raw.substring(start, index)
                    start = index + 1
                }
            }
        }
        ret += raw.substring(start, raw.length)
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

    private data class FileAliasCacheKey(
        val filePath: String,
        val fileStamp: Long,
        val mountRootsSignature: String,
    )
}
