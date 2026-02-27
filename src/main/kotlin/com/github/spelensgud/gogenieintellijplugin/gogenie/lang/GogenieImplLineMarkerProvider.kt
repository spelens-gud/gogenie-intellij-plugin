package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

class GogenieImplLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val comment = element as? PsiComment ?: return null
        if (!isGoElement(comment)) {
            return null
        }

        val profile = comment.project.service<GogenieProfileService>().getProfile()
        val annotationName = GogenieImplTargetResolver.resolveImplAnnotation(comment.text, profile) ?: return null
        val file = comment.containingFile
        val fileText = file.text
        val fileStartOffset = file.textRange.startOffset
        val commentEndOffset = comment.textRange.endOffset - fileStartOffset
        val interfaceName = GogenieImplTargetResolver.resolveFollowingInterfaceName(fileText, commentEndOffset) ?: return null
        val sourcePath = file.virtualFile?.path ?: return null

        val tooltip = "生成 ${interfaceName} 的 @$annotationName 实现"
        val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
            GogenieImplGenerationExecutor.generate(
                project = comment.project,
                sourceFilePath = sourcePath,
                annotationName = annotationName,
                interfaceName = interfaceName,
            )
        }

        return LineMarkerInfo(
            comment,
            comment.textRange,
            AllIcons.Actions.Execute,
            { tooltip },
            handler,
            GutterIconRenderer.Alignment.LEFT,
            { "gogenie impl" },
        )
    }

    private fun isGoElement(comment: PsiComment): Boolean {
        return comment.language.id.equals("go", ignoreCase = true) ||
            comment.containingFile.language.id.equals("go", ignoreCase = true)
    }
}
