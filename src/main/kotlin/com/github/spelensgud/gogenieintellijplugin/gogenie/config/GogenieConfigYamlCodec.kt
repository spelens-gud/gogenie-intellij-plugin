package com.github.spelensgud.gogenieintellijplugin.gogenie.config

object GogenieConfigYamlCodec {
    fun toYaml(config: GogenieEditableConfig): String {
        val implServices = config.implServiceNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val lines = mutableListOf<String>()
        lines += "commands:"
        lines += "  http:"
        lines += "    indent: ${quote(config.httpIndent)}"
        lines += "    api:"
        lines += "      output_path: ${quote(config.httpApiOutputPath)}"
        lines += "    router:"
        lines += "      output_path: ${quote(config.httpRouterOutputPath)}"
        lines += "    client:"
        lines += "      output_path: ${quote(config.httpClientOutputPath)}"
        lines += "  enum:"
        lines += "    indent: ${quote(config.enumIndent)}"
        lines += "    output_path: ${quote(config.enumOutputPath)}"
        lines += "  mount:"
        lines += "    name: ${quote(config.mountName)}"
        lines += "  impl:"
        lines += "    indents:"
        if (implServices.isEmpty()) {
            lines += "      - service: ${quote("service")}"
            lines += "      - service: ${quote("dao")}"
            lines += "      - service: ${quote("grpc")}"
        } else {
            for (service in implServices) {
                lines += "      - service: ${quote(service)}"
            }
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun quote(raw: String): String {
        val escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
