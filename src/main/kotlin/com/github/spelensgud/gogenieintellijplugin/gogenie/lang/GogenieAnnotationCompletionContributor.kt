package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class GogenieAnnotationCompletionContributor : CompletionContributor() {
    @Suppress("DEPRECATION")
    override fun invokeAutoPopup(position: PsiElement, typeChar: Char): Boolean {
        if (!isGoElement(position)) {
            return false
        }
        return typeChar == '@' || typeChar == '(' || typeChar == ','
    }

    init {
        extend(
            CompletionType.BASIC,
            psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val anchor = parameters.originalPosition ?: parameters.position
                    val comment = PsiTreeUtil.getParentOfType(anchor, PsiComment::class.java, false) ?: return
                    if (!isGoElement(anchor)) {
                        return
                    }

                    val caretOffset = parameters.editor.caretModel.offset
                    val offsetInComment = (caretOffset - comment.textRange.startOffset).coerceIn(0, comment.textLength)
                    val completionContext = GogenieAnnotationTextAnalyzer.resolveCompletionContext(
                        comment.text,
                        offsetInComment,
                    )
                    if (completionContext == GogenieAnnotationTextAnalyzer.CompletionContext.None) {
                        return
                    }

                    val baseProfile = comment.project.service<GogenieProfileService>().getProfile()
                    val profile = GogenieMountAliasCatalog.augmentProfile(baseProfile, comment.containingFile)

                    when (completionContext) {
                        is GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationName -> {
                            val prefix = completionContext.typedPrefix.lowercase()
                            for (spec in profile.annotationNamesSorted()) {
                                if (!spec.name.lowercase().startsWith(prefix)) {
                                    continue
                                }
                                result.addElement(
                                    LookupElementBuilder.create(spec.name)
                                        .withPresentableText("@${spec.name}")
                                        .withIcon(GogenieCompletionIcons.annotation(spec.name))
                                        .withTypeText(spec.commandSource, true)
                                        .withTailText(if (spec.snippet.isBlank()) null else "  ${spec.snippet}", true),
                                )
                            }
                        }

                        is GogenieAnnotationTextAnalyzer.CompletionContext.AnnotationOption -> {
                            val spec = profile.findSpec(completionContext.annotationName) ?: return
                            val prefix = completionContext.typedPrefix.lowercase()
                            val keys = linkedSetOf<String>()
                            spec.options.forEach { keys += it.key }
                            if (spec.allowAnyOption) {
                                keys += "field"
                            }

                            for (key in keys.sorted()) {
                                if (!key.lowercase().startsWith(prefix)) {
                                    continue
                                }
                                result.addElement(
                                    LookupElementBuilder.create("$key=")
                                        .withPresentableText("$key=")
                                        .withIcon(GogenieCompletionIcons.option(spec.name))
                                        .withTypeText(spec.commandSource, true),
                                )
                            }
                        }

                        GogenieAnnotationTextAnalyzer.CompletionContext.None -> Unit
                    }
                }
            },
        )
    }

    private fun isGoElement(element: PsiElement): Boolean {
        val languageId = element.language.id
        val fileLanguageId = element.containingFile?.language?.id
        return languageId.equals("go", ignoreCase = true) ||
            fileLanguageId.equals("go", ignoreCase = true)
    }

}
