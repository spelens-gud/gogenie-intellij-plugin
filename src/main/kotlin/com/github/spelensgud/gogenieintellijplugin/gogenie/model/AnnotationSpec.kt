package com.github.spelensgud.gogenieintellijplugin.gogenie.model

data class AnnotationSpec(
    val name: String,
    val commandSource: String,
    val options: List<AnnotationOptionSpec> = emptyList(),
    val snippet: String = "",
    val allowAnyOption: Boolean = false,
)
