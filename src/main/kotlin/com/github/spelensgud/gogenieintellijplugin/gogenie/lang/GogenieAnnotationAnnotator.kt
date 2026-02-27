package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.goide.psi.GoConstDefinition
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class GogenieAnnotationAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is PsiComment) {
            annotateComment(element, holder)
            return
        }
        if (element is GoConstDefinition) {
            annotateEnumConstDefinition(element, holder)
            return
        }
        annotateEnumConstIdentifierLeaf(element, holder)
    }

    private fun annotateComment(comment: PsiComment, holder: AnnotationHolder) {
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

        annotateHttpRouteAnchors(comment, holder, profile, base)

        val enumAnchors = GogenieEnumLinkResolver.collectCommentEnumAnchors(comment.text, profile)
        for (anchor in enumAnchors) {
            if (anchor.end <= anchor.start) {
                continue
            }
            val typeTarget = GogenieEnumNavigationService.findEnumType(
                project = comment.project,
                profile = profile,
                enumName = anchor.enumName,
            ) ?: continue
            if (!typeTarget.isValid) {
                continue
            }
            val textRange = TextRange(base + anchor.start, base + anchor.end)
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(textRange)
                .textAttributes(GogenieTextAttributes.annotationNameByName(anchor.annotationName))
                .create()
        }
    }

    private fun annotateHttpRouteAnchors(
        comment: PsiComment,
        holder: AnnotationHolder,
        profile: GogenieProfile,
        base: Int,
    ) {
        val anchors = GogenieHttpRouteLinkResolver.collectCommentRouteAnchors(comment.text, profile)
        if (anchors.isEmpty()) {
            return
        }
        val commentLength = comment.textLength
        for (anchor in anchors) {
            val start = anchor.start.coerceIn(0, commentLength)
            val end = anchor.end.coerceIn(0, commentLength)
            if (end <= start) {
                continue
            }
            val target = GogenieHttpRouteNavigationService.findRouteElement(
                comment = comment,
                anchor = anchor,
                profile = profile,
                deepSearch = false,
            ) ?: continue
            if (!target.isValid) {
                continue
            }
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(base + start, base + end))
                .textAttributes(GogenieTextAttributes.annotationNameByName(anchor.annotationName))
                .create()
        }
    }

    private fun annotateEnumConstDefinition(definition: GoConstDefinition, holder: AnnotationHolder) {
        val file = definition.containingFile
        if (!file.language.id.equals("go", ignoreCase = true)) {
            return
        }
        val identifier = definition.identifier
        val profile = definition.project.service<GogenieProfileService>().getProfile()
        val fileText = file.text
        val fileStart = file.textRange.startOffset
        val start = identifier.textRange.startOffset - fileStart
        if (start < 0 || start > fileText.length) {
            return
        }

        val semantic = GogenieEnumLinkResolver.semanticRangeAtOffset(fileText, start, profile) ?: return
        if (semantic.kind != GogenieEnumLinkResolver.EnumSemanticRange.Kind.CONST_NAME) {
            return
        }
        val typeTarget = GogenieEnumNavigationService.findEnumType(
            project = definition.project,
            profile = profile,
            enumName = semantic.enumName,
        ) ?: return
        if (!typeTarget.isValid) {
            return
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(identifier)
            .enforcedTextAttributes(GogenieTextAttributes.enforcedAttributesByName(semantic.annotationName))
            .create()
    }

    private fun annotateEnumConstIdentifierLeaf(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        if (!file.language.id.equals("go", ignoreCase = true)) {
            return
        }
        if (element.textLength <= 0 || element.firstChild != null) {
            return
        }
        val parentDef = PsiTreeUtil.getParentOfType(element, GoConstDefinition::class.java, false) ?: return
        if (parentDef.identifier.textRange != element.textRange) {
            return
        }
        annotateEnumConstDefinition(parentDef, holder)
    }

    private fun isGoElement(comment: PsiComment): Boolean {
        return comment.language.id.equals("go", ignoreCase = true) ||
            comment.containingFile.language.id.equals("go", ignoreCase = true)
    }
}
