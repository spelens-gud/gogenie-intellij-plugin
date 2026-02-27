package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.goide.psi.GoConstDefinition
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement

class GogenieImplicitUsageProvider : ImplicitUsageProvider {
    override fun isImplicitUsage(element: PsiElement): Boolean = isGogenieEnumConst(element)

    override fun isImplicitRead(element: PsiElement): Boolean = isGogenieEnumConst(element)

    override fun isImplicitWrite(element: PsiElement): Boolean = false

    private fun isGogenieEnumConst(element: PsiElement): Boolean {
        val definition = element as? GoConstDefinition ?: return false
        val file = definition.containingFile
        if (!file.language.id.equals("go", ignoreCase = true)) {
            return false
        }

        val identifier = definition.identifier
        val fileStart = file.textRange.startOffset
        val offset = identifier.textRange.startOffset - fileStart
        if (offset < 0) {
            return false
        }

        val profile = definition.project.service<GogenieProfileService>().getProfile()
        val semantic = GogenieEnumLinkResolver.semanticRangeAtOffset(file.text, offset, profile) ?: return false
        if (semantic.kind != GogenieEnumLinkResolver.EnumSemanticRange.Kind.CONST_NAME) {
            return false
        }
        return GogenieEnumNavigationService.findEnumType(
            project = definition.project,
            profile = profile,
            enumName = semantic.enumName,
        )?.isValid == true
    }
}
