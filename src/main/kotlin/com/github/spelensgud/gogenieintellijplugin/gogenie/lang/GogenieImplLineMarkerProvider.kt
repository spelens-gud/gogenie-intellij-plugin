package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import javax.swing.Icon

class GogenieImplLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            val comment = element as? PsiComment ?: continue
            if (!isGoElement(comment)) {
                continue
            }

            val profile = comment.project.service<GogenieProfileService>().getProfile()
            val markers = buildMarkers(comment, profile)
            if (markers.isNotEmpty()) {
                result.addAll(markers)
            }
        }
    }

    private fun buildMarkers(comment: PsiComment, profile: GogenieProfile): List<LineMarkerInfo<*>> {
        val matches = GogenieAnnotationTextAnalyzer.extractAnnotations(comment.text, profile)
        val matched = matches.firstOrNull { it.recognized } ?: return emptyList()
        val annotationName = matched.name
        val annotationSpec = profile.findSpec(annotationName) ?: return emptyList()
        val file = comment.containingFile
        val sourcePath = file.virtualFile?.path ?: return emptyList()

        val commands = when {
            profile.isImplAnnotation(annotationName) -> {
                val fileText = file.text
                val fileStartOffset = file.textRange.startOffset
                val commentEndOffset = comment.textRange.endOffset - fileStartOffset
                val interfaceName = GogenieImplTargetResolver.resolveFollowingInterfaceName(fileText, commentEndOffset)
                    ?: return emptyList()
                listOf(MarkerCommand.Impl(interfaceName))
            }

            annotationSpec.commandSource.equals("enum", ignoreCase = true) -> {
                val enumName = GogenieEnumLinkResolver.resolveEnumNameFromComment(comment.text, profile) ?: return emptyList()
                listOf(MarkerCommand.Enum(enumName))
            }

            else -> GogenieQuickCommandResolver.resolveAll(annotationName, profile)
                .map { MarkerCommand.Quick(it) }
        }

        if (commands.isEmpty()) {
            return emptyList()
        }

        return commands.map { command ->
            val tooltip = when (command) {
                is MarkerCommand.Impl -> "生成 ${command.interfaceName} 的 @$annotationName 实现"
                is MarkerCommand.Enum -> "生成枚举 @${annotationName}(${command.enumName})"
                is MarkerCommand.Quick -> "执行 gogenie ${command.spec.commandLabel}（@$annotationName）"
            }
            val icon = iconForCommand(annotationName, command)
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

            LineMarkerInfo(
                comment,
                comment.textRange,
                icon,
                { tooltip },
                handler,
                GutterIconRenderer.Alignment.LEFT,
                { "gogenie ${annotationName.lowercase()} marker" },
            )
        }
    }

    private fun iconForCommand(annotationName: String, command: MarkerCommand): Icon {
        return when (command) {
            is MarkerCommand.Quick -> GogenieCompletionIcons.command(annotationName, command.spec.commandLabel)
            else -> GogenieCompletionIcons.annotation(annotationName)
        }
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
