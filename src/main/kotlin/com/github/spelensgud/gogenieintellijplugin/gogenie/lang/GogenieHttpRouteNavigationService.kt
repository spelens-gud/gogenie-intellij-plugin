package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

object GogenieHttpRouteNavigationService {
    fun findRouteElement(
        comment: PsiComment,
        anchor: GogenieHttpRouteLinkResolver.HttpRouteAnchor,
        profile: GogenieProfile,
        deepSearch: Boolean = false,
    ): PsiElement? {
        val file = comment.containingFile ?: return null
        val fileText = file.text
        val fileStart = file.textRange.startOffset
        val commentStart = comment.textRange.startOffset - fileStart
        val commentEnd = comment.textRange.endOffset - fileStart
        if (commentStart < 0 || commentEnd < 0) {
            return null
        }

        val context = GogenieHttpRouteLinkResolver.resolveRouteContext(
            fileText = fileText,
            commentStartOffset = commentStart,
            commentEndOffset = commentEnd,
            anchor = anchor,
            profile = profile,
        ) ?: return null

        val basePath = comment.project.basePath ?: return null
        val outputRoots = linkedSetOf(profile.httpRouterOutputPath, profile.httpApiOutputPath)
        for (outputRoot in outputRoots) {
            val rootDir = resolveOutputDir(basePath, outputRoot) ?: continue
            val direct = rootDir.resolve(context.group).resolve("${context.filename}.go").normalize()
            findRouteInPath(comment.project, direct, context)?.let { return it }
        }

        if (!deepSearch) {
            return null
        }

        for (outputRoot in outputRoots) {
            val rootDir = resolveOutputDir(basePath, outputRoot) ?: continue
            findRouteInDirectory(comment.project, rootDir, context)?.let { return it }
        }
        return null
    }

    private fun findRouteInPath(
        project: Project,
        path: Path,
        context: GogenieHttpRouteLinkResolver.HttpRouteContext,
    ): PsiElement? {
        val psiFile = toPsiFile(project, path) ?: return null
        return findRouteInPsiFile(psiFile, context)
    }

    private fun findRouteInDirectory(
        project: Project,
        rootDir: Path,
        context: GogenieHttpRouteLinkResolver.HttpRouteContext,
    ): PsiElement? {
        if (!Files.isDirectory(rootDir)) {
            return null
        }

        var scanned = 0
        Files.walk(rootDir).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (scanned >= 500) {
                    break
                }
                if (!path.isRegularFile() || path.extension.lowercase() != "go") {
                    continue
                }
                scanned++
                val content = runCatching { Files.readString(path) }.getOrNull() ?: continue
                val routeLiteral = "\"${context.fullRoute}\""
                if (!content.contains(routeLiteral)) {
                    continue
                }

                val psiFile = toPsiFile(project, path) ?: continue
                val target = findRouteInPsiFile(psiFile, context)
                if (target != null) {
                    return target
                }
            }
        }
        return null
    }

    private fun findRouteInPsiFile(
        psiFile: PsiFile,
        context: GogenieHttpRouteLinkResolver.HttpRouteContext,
    ): PsiElement? {
        val text = psiFile.text
        val routeLiteral = "\"${context.fullRoute}\""
        val handlerName = Regex.escape(context.handler)
        val exact = Regex(
            """router\.\w+\(\s*(${Regex.escape(routeLiteral)})\s*,\s*svcH\(svc\.$handlerName\)\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        val routeOnly = Regex("""(${Regex.escape(routeLiteral)})""")
        val group = exact.find(text)?.groups?.get(1) ?: routeOnly.find(text)?.groups?.get(1) ?: return null
        val start = group.range.first
        val offset = if (group.value.startsWith('"')) start + 1 else start
        return psiFile.findElementAt(offset)
    }

    private fun resolveOutputDir(basePath: String, outputPath: String): Path? {
        val base = runCatching { Paths.get(basePath) }.getOrNull() ?: return null
        val out = runCatching { Paths.get(outputPath.ifBlank { "./apis" }) }.getOrNull() ?: return null
        return if (out.isAbsolute) out.normalize() else base.resolve(out).normalize()
    }

    private fun toPsiFile(project: Project, path: Path): PsiFile? {
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return null
        return PsiManager.getInstance(project).findFile(vFile)
    }
}
