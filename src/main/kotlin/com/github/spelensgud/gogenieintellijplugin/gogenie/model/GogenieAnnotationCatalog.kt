package com.github.spelensgud.gogenieintellijplugin.gogenie.model

object GogenieAnnotationCatalog {
    private val autowireOptions = listOf(
        AnnotationOptionSpec("set", "注入分组"),
        AnnotationOptionSpec("new", "指定构造函数"),
        AnnotationOptionSpec("skip", "跳过字段"),
        AnnotationOptionSpec("init", "初始化标记"),
        AnnotationOptionSpec("config", "配置标记"),
    )

    private val httpOptions = listOf(
        AnnotationOptionSpec("method", "HTTP 方法"),
        AnnotationOptionSpec("route", "路由路径"),
        AnnotationOptionSpec("ns", "命名空间过滤"),
    )

    private val serviceLikeOptions = listOf(
        AnnotationOptionSpec("route", "路由前缀"),
        AnnotationOptionSpec("group", "路由组"),
        AnnotationOptionSpec("filename", "生成文件名"),
        AnnotationOptionSpec("proto", "关联 proto service 名称"),
    )

    private val mountOptions = listOf(
        AnnotationOptionSpec("field", "挂载参数名（示例：field=Type）"),
    )

    private val swaggerTags = listOf(
        "title",
        "version",
        "description",
        "BasePath",
        "Summary",
        "Description",
        "Tags",
        "Accept",
        "Produce",
        "Param",
        "Success",
        "Failure",
        "Router",
    )

    fun build(dynamicConfig: GogenieDynamicConfig): GogenieProfile {
        val specs = linkedMapOf<String, AnnotationSpec>()

        fun put(spec: AnnotationSpec) {
            specs.putIfAbsent(spec.name.lowercase(), spec)
        }

        put(
            AnnotationSpec(
                name = "autowire",
                commandSource = "autowire",
                options = autowireOptions,
                snippet = "@autowire(set=...,new=...,skip=...)",
            ),
        )
        put(AnnotationSpec("autowire.init", "autowire", snippet = "@autowire.init(set=...)"))
        put(AnnotationSpec("autowire.config", "autowire", snippet = "@autowire.config(set=...)"))

        put(
            AnnotationSpec(
                name = "http",
                commandSource = "http",
                options = httpOptions,
                snippet = "@http(method=get,route=\"/list\")",
            ),
        )
        put(AnnotationSpec("http.get", "http", snippet = "@http.get(\"/list\")"))
        put(AnnotationSpec("http.post", "http", snippet = "@http.post(\"/create\")"))
        put(AnnotationSpec("http.delete", "http", snippet = "@http.delete(\"/delete\")"))

        put(
            AnnotationSpec(
                name = dynamicConfig.enumIndent,
                commandSource = "enum",
                snippet = "@${dynamicConfig.enumIndent}(TypeName)",
            ),
        )

        put(
            AnnotationSpec(
                name = dynamicConfig.mountName,
                commandSource = "mount",
                options = mountOptions,
                snippet = "@${dynamicConfig.mountName}(field=Type)",
                allowAnyOption = true,
            ),
        )

        for (serviceLikeName in dynamicConfig.serviceLikeNames()) {
            put(
                AnnotationSpec(
                    name = serviceLikeName,
                    commandSource = "impl/http",
                    options = serviceLikeOptions,
                    snippet = "@$serviceLikeName(name,route=\"/...\",group=\"...\")",
                ),
            )
        }

        put(AnnotationSpec("grpc_server", "grpc", snippet = "@grpc_server()"))
        put(AnnotationSpec("rule", "rule", snippet = "@rule(业务描述)"))
        put(AnnotationSpec("rule-hash", "rule", snippet = "@rule-hash: abcdef1234567890"))

        for (tag in swaggerTags) {
            put(AnnotationSpec(tag, "swagger", snippet = "@$tag ..."))
        }

        return GogenieProfile(
            specs = specs.values.toList(),
            implAnnotationNames = dynamicConfig.implServiceNames,
            enumOutputPath = dynamicConfig.enumOutputPath,
            configPath = dynamicConfig.configPath?.toString(),
            parseError = dynamicConfig.parseError,
        )
    }
}
