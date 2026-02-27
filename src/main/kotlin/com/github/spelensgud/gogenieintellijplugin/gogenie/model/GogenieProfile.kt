package com.github.spelensgud.gogenieintellijplugin.gogenie.model

data class GogenieProfile(
    val specs: List<AnnotationSpec>,
    val implAnnotationNames: Set<String> = emptySet(),
    val enumOutputPath: String = GogenieDynamicConfig.DEFAULT_ENUM_OUTPUT_PATH,
    val httpApiOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_API_OUTPUT_PATH,
    val httpRouterOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_ROUTER_OUTPUT_PATH,
    val httpClientOutputPath: String = GogenieDynamicConfig.DEFAULT_HTTP_CLIENT_OUTPUT_PATH,
    val configPath: String? = null,
    val parseError: String? = null,
) {
    private val specsByLowerName = specs.associateBy { it.name.lowercase() }
    private val implAnnotations = implAnnotationNames.map { it.lowercase() }.toSet()

    fun findSpec(name: String): AnnotationSpec? = specsByLowerName[name.lowercase()]

    fun optionSpecsFor(annotationName: String): List<AnnotationOptionSpec> {
        return findSpec(annotationName)?.options ?: emptyList()
    }

    fun annotationNamesSorted(): List<AnnotationSpec> {
        return specs.sortedBy { it.name }
    }

    fun isImplAnnotation(name: String): Boolean {
        return implAnnotations.contains(name.lowercase())
    }
}
