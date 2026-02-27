package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

class GogenieGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?,
    ): Array<PsiElement>? {
        val element = sourceElement ?: return null
        val file = element.containingFile ?: return null
        if (!isGoElement(element)) {
            return null
        }
        if (element is PsiComment) {
            return null
        }

        val profile = element.project.service<GogenieProfileService>().getProfile()
        val target = GogenieEnumLinkResolver.resolveAtOffset(file.text, offset, profile) ?: return null
        val resolved = if (target.constName == null) {
            GogenieEnumNavigationService.findEnumType(element.project, profile, target.enumName)
        } else {
            GogenieEnumNavigationService.findEnumConst(
                project = element.project,
                profile = profile,
                enumName = target.enumName,
                constName = target.constName,
            ) ?: GogenieEnumNavigationService.findEnumType(element.project, profile, target.enumName)
        } ?: return null

        return arrayOf(resolved)
    }

    override fun getActionText(context: com.intellij.openapi.actionSystem.DataContext): String? = null

    private fun isGoElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        val fileLanguageId = element.containingFile.language.id
        return languageId.equals("go", ignoreCase = true) ||
            fileLanguageId.equals("go", ignoreCase = true)
    }
}
