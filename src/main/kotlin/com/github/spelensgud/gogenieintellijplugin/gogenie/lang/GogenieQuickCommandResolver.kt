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
        val spec = profile.findSpec(annotationName) ?: return null
        val source = spec.commandSource.lowercase()
        val normalized = annotationName.lowercase()

        return when {
            source == "autowire" -> GogenieQuickCommandSpec(
                commandLabel = "autowire",
                args = listOf("autowire"),
            )

            source == "enum" -> GogenieQuickCommandSpec(
                commandLabel = "enum",
                args = listOf("enum"),
            )

            source == "mount" -> GogenieQuickCommandSpec(
                commandLabel = "mount",
                args = listOf("mount"),
            )

            source == "rule" -> GogenieQuickCommandSpec(
                commandLabel = "rule",
                args = listOf("rule"),
                scopeFlag = "--scope",
                scopeTarget = GogenieQuickCommandSpec.ScopeTarget.FILE,
            )

            source == "swagger" -> GogenieQuickCommandSpec(
                commandLabel = "http swagger",
                args = listOf("http", "swagger"),
            )

            source == "http" -> GogenieQuickCommandSpec(
                commandLabel = "http api",
                args = listOf("http", "api"),
            )

            source == "impl/http" && !profile.isImplAnnotation(normalized) -> GogenieQuickCommandSpec(
                commandLabel = "http router",
                args = listOf("http", "router"),
            )

            source == "grpc" && !profile.isImplAnnotation(normalized) -> GogenieQuickCommandSpec(
                commandLabel = "impl grpc",
                args = listOf("impl", "grpc"),
                scopeFlag = "--scope",
                scopeTarget = GogenieQuickCommandSpec.ScopeTarget.FILE,
            )

            else -> null
        }
    }
}

