package com.github.spelensgud.gogenieintellijplugin.gogenie.model

import java.nio.file.Path

data class GogenieDynamicConfig(
    val httpIndent: String = DEFAULT_HTTP_INDENT,
    val enumIndent: String = DEFAULT_ENUM_INDENT,
    val mountName: String = DEFAULT_MOUNT_NAME,
    val implServiceNames: Set<String> = DEFAULT_IMPL_SERVICE_NAMES,
    val configPath: Path? = null,
    val parseError: String? = null,
) {
    fun serviceLikeNames(): Set<String> {
        return (implServiceNames + httpIndent).filter { it.isNotBlank() }.toSet()
    }

    companion object {
        const val DEFAULT_HTTP_INDENT = "service"
        const val DEFAULT_ENUM_INDENT = "enum"
        const val DEFAULT_MOUNT_NAME = "mount"
        val DEFAULT_IMPL_SERVICE_NAMES = setOf("service", "dao", "grpc")

        fun defaults(configPath: Path? = null): GogenieDynamicConfig {
            return GogenieDynamicConfig(configPath = configPath)
        }
    }
}
