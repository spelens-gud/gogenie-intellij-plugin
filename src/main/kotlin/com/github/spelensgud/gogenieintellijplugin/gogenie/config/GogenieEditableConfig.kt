package com.github.spelensgud.gogenieintellijplugin.gogenie.config

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieDynamicConfig

data class GogenieEditableConfig(
    val httpIndent: String = GogenieDynamicConfig.DEFAULT_HTTP_INDENT,
    val httpApiOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_API_OUTPUT_PATH,
    val httpRouterOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_ROUTER_OUTPUT_PATH,
    val httpClientOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_CLIENT_OUTPUT_PATH,
    val enumIndent: String = GogenieDynamicConfig.DEFAULT_ENUM_INDENT,
    val enumOutputPath: String = GogenieDynamicConfig.DEFAULT_ENUM_OUTPUT_PATH,
    val mountName: String = GogenieDynamicConfig.DEFAULT_MOUNT_NAME,
    val implServiceNames: List<String> = GogenieDynamicConfig.DEFAULT_IMPL_SERVICE_NAMES.toList(),
) {
    companion object {
        fun defaults(): GogenieEditableConfig = GogenieEditableConfig()

        fun fromDynamic(config: GogenieDynamicConfig): GogenieEditableConfig {
            return GogenieEditableConfig(
                httpIndent = config.httpIndent,
                httpApiOutputPath = config.httpApiOutputPath,
                httpRouterOutputPath = config.httpRouterOutputPath,
                httpClientOutputPath = config.httpClientOutputPath,
                enumIndent = config.enumIndent,
                enumOutputPath = config.enumOutputPath,
                mountName = config.mountName,
                implServiceNames = sortImplServices(config.implServiceNames),
            )
        }

        private fun sortImplServices(values: Set<String>): List<String> {
            val wanted = listOf("service", "dao", "grpc")
            val normalized = values.filter { it.isNotBlank() }.toSet()
            val result = mutableListOf<String>()
            for (name in wanted) {
                if (normalized.contains(name)) {
                    result += name
                }
            }
            result += normalized.filter { it !in wanted }.sorted()
            return if (result.isEmpty()) GogenieDynamicConfig.DEFAULT_IMPL_SERVICE_NAMES.toList() else result
        }
    }
}
