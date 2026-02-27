package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieProfileService
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext

class GogenieMountReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiComment::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val comment = element as? PsiComment ?: return PsiReference.EMPTY_ARRAY
                    if (!isGoComment(comment)) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    val baseProfile = comment.project.service<GogenieProfileService>().getProfile()
                    val profile = GogenieMountAliasCatalog.augmentProfile(baseProfile, comment.containingFile)
                    val anchors = GogenieMountLinkResolver.collectCommentValueAnchors(comment.text, profile)
                    if (anchors.isEmpty()) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    return anchors
                        .filter { it.end > it.start }
                        .map { anchor ->
                            MountValueReference(
                                comment = comment,
                                range = TextRange(anchor.start, anchor.end),
                                anchor = anchor,
                            )
                        }
                        .toTypedArray()
                }
            },
        )
    }

    private fun isGoComment(comment: PsiComment): Boolean {
        return comment.language.id.equals("go", ignoreCase = true) ||
            comment.containingFile.language.id.equals("go", ignoreCase = true)
    }

    private class MountValueReference(
        comment: PsiComment,
        range: TextRange,
        private val anchor: GogenieMountLinkResolver.MountValueAnchor,
    ) : PsiReferenceBase<PsiComment>(comment, range, false) {
        override fun resolve(): PsiElement? {
            val baseProfile = myElement.project.service<GogenieProfileService>().getProfile()
            val profile = GogenieMountAliasCatalog.augmentProfile(baseProfile, myElement.containingFile)
            return myElement.project.service<GogenieMountBindingService>().resolveValueTarget(
                profile = profile,
                annotationName = anchor.annotationName,
                valueName = anchor.valueName,
                contextFilePath = myElement.containingFile.virtualFile?.path,
            )
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }
}
