package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.goide.GoFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class GogenieMountBindingService(private val project: Project) {
    @Volatile
    private var cache: CacheState? = null

    fun resolveValueTarget(
        profile: GogenieProfile,
        annotationName: String,
        valueName: String,
        contextFilePath: String? = null,
    ): PsiElement? {
        if (valueName.isBlank()) {
            return null
        }
        val alias = annotationName.lowercase()
        val bindings = index(profile)[alias].orEmpty()
        if (bindings.isEmpty()) {
            return null
        }

        val sorted = prioritizeBindings(bindings, contextFilePath)
        val byField = sorted.firstNotNullOfOrNull { binding ->
            val offset = findFieldOffset(binding, valueName) ?: return@firstNotNullOfOrNull null
            findElement(binding.filePath, offset)
        }
        if (byField != null) {
            return byField
        }

        return sorted.firstNotNullOfOrNull { binding ->
            if (!binding.structName.equals(valueName, ignoreCase = false)) {
                null
            } else {
                findElement(binding.filePath, binding.structNameOffset)
            }
        }
    }

    private fun findFieldOffset(binding: LocatedBinding, valueName: String): Int? {
        binding.fieldOffsets[valueName]?.let { return it }
        val ignoreCase = binding.fieldOffsets.entries.firstOrNull { it.key.equals(valueName, ignoreCase = true) }
        return ignoreCase?.value
    }

    private fun prioritizeBindings(bindings: List<LocatedBinding>, contextFilePath: String?): List<LocatedBinding> {
        if (contextFilePath.isNullOrBlank()) {
            return bindings
        }
        val contextDir = contextFilePath.substringBeforeLast('/', "")
        return bindings.sortedByDescending { binding ->
            binding.filePath.substringBeforeLast('/', "") == contextDir
        }
    }

    private fun findElement(filePath: String, offset: Int): PsiElement? {
        if (offset < 0) {
            return null
        }
        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null
        return psiFile.findElementAt(offset)
    }

    private fun index(profile: GogenieProfile): Map<String, List<LocatedBinding>> {
        val mountRoots = GogenieMountAliasCatalog.mountRootNames(profile)
        if (mountRoots.isEmpty()) {
            return emptyMap()
        }

        val modCount = PsiModificationTracker.getInstance(project).modificationCount
        val rootSignature = mountRoots.sorted().joinToString(",")
        val current = cache
        if (current != null && current.modCount == modCount && current.rootSignature == rootSignature) {
            return current.index
        }

        val computed = scanBindings(mountRoots)
        cache = CacheState(modCount, rootSignature, computed)
        return computed
    }

    private fun scanBindings(mountRoots: Set<String>): Map<String, List<LocatedBinding>> {
        val result = linkedMapOf<String, MutableList<LocatedBinding>>()
        val psiManager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(GoFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        for (vFile in files) {
            val text = psiManager.findFile(vFile)?.text ?: runCatching { VfsUtilCore.loadText(vFile) }.getOrNull() ?: continue
            val bindings = GogenieMountLinkResolver.collectMountBindingsFromFile(text, mountRoots)
            if (bindings.isEmpty()) {
                continue
            }
            for (binding in bindings) {
                val located = LocatedBinding(
                    alias = binding.alias.lowercase(),
                    filePath = vFile.path,
                    structName = binding.structName,
                    structNameOffset = binding.structNameOffset,
                    fieldOffsets = binding.fieldOffsets,
                )
                result.getOrPut(located.alias) { mutableListOf() }.add(located)
            }
        }
        return result
    }

    private data class CacheState(
        val modCount: Long,
        val rootSignature: String,
        val index: Map<String, List<LocatedBinding>>,
    )

    private data class LocatedBinding(
        val alias: String,
        val filePath: String,
        val structName: String,
        val structNameOffset: Int,
        val fieldOffsets: Map<String, Int>,
    )
}
