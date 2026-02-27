package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.github.spelensgud.gogenieintellijplugin.gogenie.model.GogenieProfile

data class GogenieQuickCommandSpec(
    val commandLabel: String,
    val args: List<String>,
    val scopeFlag: String? = null,
    val scopeTarget: ScopeTarget = ScopeTarget.NONE,
) {
    enum class ScopeTarget {
        NONE,
        FILE,
        DIR,
    }
}

object GogenieQuickCommandResolver {
    fun resolve(annotationName: String, profile: GogenieProfile): GogenieQuickCommandSpec? {
        return resolveAll(annotationName, profile).firstOrNull()
    }

    fun resolveAll(annotationName: String, profile: GogenieProfile): List<GogenieQuickCommandSpec> {
        val spec = profile.findSpec(annotationName) ?: return emptyList()
        val source = spec.commandSource.lowercase()
        val normalized = annotationName.lowercase()

        return when {
            source == "autowire" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "autowire",
                    args = listOf("autowire"),
                ),
            )

            source == "enum" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "enum",
                    args = listOf("enum"),
                ),
            )

            source == "mount" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "mount",
                    args = listOf("mount"),
                ),
            )

            source == "rule" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "rule",
                    args = listOf("rule"),
                    scopeFlag = "--scope",
                    scopeTarget = GogenieQuickCommandSpec.ScopeTarget.FILE,
                ),
            )

            source == "swagger" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "http swagger",
                    args = listOf("http", "swagger"),
                ),
            )

            source == "http" -> listOf(
                GogenieQuickCommandSpec(
                    commandLabel = "http api",
                    args = listOf("http", "api"),
                ),
                GogenieQuickCommandSpec(
                    commandLabel = "http client",
                    args = listOf("http", "client"),
                ),
            )

            source == "impl/http" && !profile.isImplAnnotation(normalized) -> listOf(GogenieQuickCommandSpec(
                commandLabel = "http router",
                args = listOf("http", "router"),
            ))

            source == "grpc" && !profile.isImplAnnotation(normalized) -> listOf(GogenieQuickCommandSpec(
                commandLabel = "impl grpc",
                args = listOf("impl", "grpc"),
                scopeFlag = "--scope",
                scopeTarget = GogenieQuickCommandSpec.ScopeTarget.FILE,
            ))

            else -> emptyList()
        }
    }
}
