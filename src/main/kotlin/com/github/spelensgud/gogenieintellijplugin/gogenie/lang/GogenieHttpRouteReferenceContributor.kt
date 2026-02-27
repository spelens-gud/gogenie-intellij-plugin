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

class GogenieHttpRouteReferenceContributor : PsiReferenceContributor() {
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

                    val profile = comment.project.service<GogenieProfileService>().getProfile()
                    val anchors = GogenieHttpRouteLinkResolver.collectCommentRouteAnchors(comment.text, profile)
                    if (anchors.isEmpty()) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    return anchors
                        .filter { it.end > it.start }
                        .map { anchor ->
                            HttpRouteCommentReference(
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

    private class HttpRouteCommentReference(
        comment: PsiComment,
        range: TextRange,
        private val anchor: GogenieHttpRouteLinkResolver.HttpRouteAnchor,
    ) : PsiReferenceBase<PsiComment>(comment, range, false) {
        override fun resolve(): PsiElement? {
            val profile = myElement.project.service<GogenieProfileService>().getProfile()
            return GogenieHttpRouteNavigationService.findRouteElement(
                comment = myElement,
                anchor = anchor,
                profile = profile,
                deepSearch = true,
            )
        }

        override fun getVariants(): Array<Any> = emptyArray()
    }
}
