package com.github.spelensgud.gogenieintellijplugin.gogenie.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object GogenieConfigLocator {
    private val candidates = listOf(
        ".gogenie/config.yaml",
        ".gogenie/config.yml",
        ".gogenie/config.toml",
        ".gogenie.yaml",
        ".gogenie.yml",
        ".gogenie.toml",
    )

    fun find(projectBasePath: String?): Path? {
        if (projectBasePath.isNullOrBlank()) {
            return null
        }
        return find(Paths.get(projectBasePath))
    }

    fun find(projectRoot: Path): Path? {
        for (candidate in candidates) {
            val file = projectRoot.resolve(candidate)
            if (Files.isRegularFile(file)) {
                return file
            }
        }
        return null
    }
}
