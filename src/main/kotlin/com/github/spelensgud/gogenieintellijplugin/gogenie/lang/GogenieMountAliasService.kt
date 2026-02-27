package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.goide.GoFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class GogenieMountAliasService(private val project: Project) {
    @Volatile
    private var cache: CacheState? = null

    fun getAliases(profile: GogenieProfile): Set<String> {
        val mountRoots = GogenieMountAliasCatalog.mountRootNames(profile)
        if (mountRoots.isEmpty()) {
            return emptySet()
        }

        val modCount = PsiModificationTracker.getInstance(project).modificationCount
        val rootSignature = mountRoots.sorted().joinToString(",")
        val current = cache
        if (current != null && current.modCount == modCount && current.rootSignature == rootSignature) {
            return current.aliases
        }

        val aliases = scanAliases(mountRoots)
        cache = CacheState(modCount, rootSignature, aliases)
        return aliases
    }

    private fun scanAliases(mountRoots: Set<String>): Set<String> {
        val aliases = linkedSetOf<String>()
        val psiManager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(GoFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        for (vFile in files) {
            val text = psiManager.findFile(vFile)?.text ?: runCatching { VfsUtilCore.loadText(vFile) }.getOrNull() ?: continue
            aliases += GogenieMountAliasCatalog.collectAliasesFromText(text, mountRoots)
        }
        return aliases
    }

    private data class CacheState(
        val modCount: Long,
        val rootSignature: String,
        val aliases: Set<String>,
    )
}
