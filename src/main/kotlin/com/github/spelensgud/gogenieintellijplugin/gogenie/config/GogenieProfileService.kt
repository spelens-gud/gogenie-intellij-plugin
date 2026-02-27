package com.github.spelensgud.gogenieintellijplugin.gogenie.config

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieAnnotationCatalog
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig
import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class GogenieProfileService(private val project: Project) {
    @Volatile
    private var cache: CacheState? = null

    fun getProfile(): GogenieProfile {
        val configPath = GogenieConfigLocator.find(project.basePath)
        val lastModified = configPath?.safeLastModifiedMillis() ?: NO_FILE_STAMP

        val current = cache
        if (current != null && current.configPath == configPath && current.lastModified == lastModified) {
            return current.profile
        }

        val dynamicConfig = loadDynamicConfig(configPath)
        val profile = GogenieAnnotationCatalog.build(dynamicConfig)
        cache = CacheState(configPath, lastModified, profile)
        return profile
    }

    private fun loadDynamicConfig(configPath: Path?): GogenieDynamicConfig {
        if (configPath == null) {
            return GogenieDynamicConfig.defaults()
        }
        return GogenieConfigParser.parse(configPath)
    }

    private fun Path.safeLastModifiedMillis(): Long {
        return try {
            Files.getLastModifiedTime(this).toMillis()
        } catch (_: Exception) {
            NO_FILE_STAMP
        }
    }

    private data class CacheState(
        val configPath: Path?,
        val lastModified: Long,
        val profile: GogenieProfile,
    )

    private companion object {
        const val NO_FILE_STAMP = -1L
    }
}
