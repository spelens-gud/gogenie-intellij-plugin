package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement

class GogenieAnnotationAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val comment = element as? PsiComment ?: return
        if (!isGoElement(comment)) {
            return
        }

        val profile = comment.project.service<GogenieProfileService>().getProfile()
        val matches = GogenieAnnotationTextAnalyzer.extractAnnotations(comment.text, profile)
        if (matches.isEmpty()) {
            return
        }

        val base = comment.textRange.startOffset
        for (match in matches) {
            val nameRange = TextRange(base + match.nameSpan.start, base + match.nameSpan.end)
            if (match.recognized) {
                val nameAttr = GogenieTextAttributes.annotationNameByName(match.name)
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameRange)
                    .textAttributes(nameAttr)
                    .create()

                for (option in match.options) {
                    val optionRange = TextRange(base + option.span.start, base + option.span.end)
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(optionRange)
                        .textAttributes(GogenieTextAttributes.ANNOTATION_OPTION)
                        .create()
                }
            } else {
                holder.newAnnotation(HighlightSeverity.WEAK_WARNING, "未识别的 gogenie 注解")
                    .range(nameRange)
                    .create()
            }
        }
    }

    private fun isGoElement(comment: PsiComment): Boolean {
        return comment.language.id.equals("go", ignoreCase = true) ||
            comment.containingFile.language.id.equals("go", ignoreCase = true)
    }
}
