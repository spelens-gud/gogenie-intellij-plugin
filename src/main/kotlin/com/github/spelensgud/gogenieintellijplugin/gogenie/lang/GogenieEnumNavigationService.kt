package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.nio.file.Paths

object GogenieEnumNavigationService {
    fun findEnumType(project: Project, profile: GogenieProfile, enumName: String): PsiElement? {
        val psiFile = resolveGeneratedFile(project, profile, enumName) ?: return null
        val pattern = Regex("""(?m)^\s*type\s+(${Regex.escape(enumName)})\b""")
        val group = pattern.find(psiFile.text)?.groups?.get(1) ?: return null
        return psiFile.findElementAt(group.range.first)
    }

    fun findEnumConst(project: Project, profile: GogenieProfile, enumName: String, constName: String): PsiElement? {
        val psiFile = resolveGeneratedFile(project, profile, enumName) ?: return null
        val withType = Regex("""(?m)^\s*(${Regex.escape(constName)})\s+${Regex.escape(enumName)}\s*=""")
        val fallback = Regex("""(?m)^\s*(${Regex.escape(constName)})\b.*=""")
        val group = withType.find(psiFile.text)?.groups?.get(1)
            ?: fallback.find(psiFile.text)?.groups?.get(1)
            ?: return null
        return psiFile.findElementAt(group.range.first)
    }

    private fun resolveGeneratedFile(project: Project, profile: GogenieProfile, enumName: String): PsiFile? {
        val basePath = project.basePath ?: return null
        val outputPath = profile.enumOutputPath.ifBlank { GogenieDynamicConfig.DEFAULT_ENUM_OUTPUT_PATH }

        val output = runCatching { Paths.get(outputPath) }.getOrNull() ?: return null
        val base = Paths.get(basePath)
        val resolvedDir = if (output.isAbsolute) output else base.resolve(output)
        val targetPath = resolvedDir.normalize().resolve("${snakeCase(enumName)}.go").normalize()

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(targetPath) ?: return null
        return PsiManager.getInstance(project).findFile(vFile)
    }

    private fun snakeCase(value: String): String {
        return value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("[\\s\\-]+"), "_")
            .lowercase()
    }
}
