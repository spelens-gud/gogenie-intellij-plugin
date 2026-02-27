package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
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
        val matches = GogenieAnnotationTextAnalyzer.extractAnnotations(comment.text, profile)
        val matched = matches.firstOrNull { it.recognized } ?: return null
        val annotationName = matched.name
        val annotationSpec = profile.findSpec(annotationName) ?: return null
        val file = comment.containingFile
        val fileText = file.text
        val fileStartOffset = file.textRange.startOffset
        val commentEndOffset = comment.textRange.endOffset - fileStartOffset
        val sourcePath = file.virtualFile?.path ?: return null

        val command = if (profile.isImplAnnotation(annotationName)) {
            val interfaceName = GogenieImplTargetResolver.resolveFollowingInterfaceName(fileText, commentEndOffset)
                ?: return null
            MarkerCommand.Impl(interfaceName)
        } else if (annotationSpec.commandSource.equals("enum", ignoreCase = true)) {
            val enumName = GogenieEnumLinkResolver.resolveEnumNameFromComment(comment.text, profile) ?: return null
            MarkerCommand.Enum(enumName)
        } else {
            val quick = GogenieQuickCommandResolver.resolve(annotationName, profile) ?: return null
            MarkerCommand.Quick(quick)
        }

        val tooltip = when (command) {
            is MarkerCommand.Impl -> "生成 ${command.interfaceName} 的 @$annotationName 实现"
            is MarkerCommand.Enum -> "生成枚举 @${annotationName}(${command.enumName})"
            is MarkerCommand.Quick -> "执行 gogenie ${command.spec.commandLabel}（@$annotationName）"
        }
        val handler = GutterIconNavigationHandler<PsiElement> { _, _ ->
            when (command) {
                is MarkerCommand.Impl -> {
                    GogenieImplGenerationExecutor.generate(
                        project = comment.project,
                        sourceFilePath = sourcePath,
                        annotationName = annotationName,
                        interfaceName = command.interfaceName,
                    )
                }

                is MarkerCommand.Enum -> {
                    GogenieImplGenerationExecutor.generateEnum(
                        project = comment.project,
                        sourceFilePath = sourcePath,
                        enumName = command.enumName,
                    )
                }

                is MarkerCommand.Quick -> {
                    GogenieImplGenerationExecutor.runQuickCommand(
                        project = comment.project,
                        sourceFilePath = sourcePath,
                        annotationName = annotationName,
                        command = command.spec,
                    )
                }
            }
        }

        return LineMarkerInfo(
            comment,
            comment.textRange,
            GogenieCompletionIcons.annotation(annotationName),
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

    private sealed interface MarkerCommand {
        data class Impl(val interfaceName: String) : MarkerCommand
        data class Enum(val enumName: String) : MarkerCommand
        data class Quick(val spec: GogenieQuickCommandSpec) : MarkerCommand
    }
}
