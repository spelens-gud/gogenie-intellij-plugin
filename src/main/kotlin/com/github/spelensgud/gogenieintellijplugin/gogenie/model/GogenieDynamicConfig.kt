package com.github.spelensgud.gogenieintellijplugin.gogenie.model

import java.nio.file.Path

data class GogenieDynamicConfig(
    val httpIndent: String = DEFAULT_HTTP_INDENT,
    val enumIndent: String = DEFAULT_ENUM_INDENT,
    val enumOutputPath: String = DEFAULT_ENUM_OUTPUT_PATH,
    val mountName: String = DEFAULT_MOUNT_NAME,
    val implServiceNames: Set<String> = DEFAULT_IMPL_SERVICE_NAMES,
    val httpApiOutputPath: String = DEFAULT_HTTP_API_OUTPUT_PATH,
    val httpRouterOutputPath: String = DEFAULT_HTTP_ROUTER_OUTPUT_PATH,
    val httpClientOutputPath: String = DEFAULT_HTTP_CLIENT_OUTPUT_PATH,
    val configPath: Path? = null,
    val parseError: String? = null,
) {
    fun serviceLikeNames(): Set<String> {
        return (implServiceNames + httpIndent).filter { it.isNotBlank() }.toSet()
    }

    companion object {
        const val DEFAULT_HTTP_INDENT = "service"
        const val DEFAULT_ENUM_INDENT = "enum"
        const val DEFAULT_ENUM_OUTPUT_PATH = "./internal/enum"
        const val DEFAULT_MOUNT_NAME = "mount"
        const val DEFAULT_HTTP_API_OUTPUT_PATH = "./apis"
        const val DEFAULT_HTTP_ROUTER_OUTPUT_PATH = "./apis"
        const val DEFAULT_HTTP_CLIENT_OUTPUT_PATH = "./clients"
        val DEFAULT_IMPL_SERVICE_NAMES = setOf("service", "dao", "grpc")

        fun defaults(configPath: Path? = null): GogenieDynamicConfig {
            return GogenieDynamicConfig(configPath = configPath)
        }
    }
}
