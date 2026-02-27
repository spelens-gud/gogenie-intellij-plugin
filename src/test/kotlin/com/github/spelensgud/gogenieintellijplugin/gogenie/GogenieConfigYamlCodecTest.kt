package com.github.spelensgud.gogenieintellijplugin.gogenie

import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieConfigYamlCodec
import com.github.spelensgud.gogenieintellijplugin.gogenie.config.GogenieEditableConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class GogenieConfigYamlCodecTest {
    @Test
    fun `should render editable config to yaml`() {
        val yaml = GogenieConfigYamlCodec.toYaml(
            GogenieEditableConfig(
                httpIndent = "api",
                httpApiOutputPath = "./gen/apis",
                httpRouterOutputPath = "./gen/router",
                httpClientOutputPath = "./gen/client",
                enumIndent = "state",
                enumOutputPath = "./internal/enum",
                mountName = "mount",
                implServiceNames = listOf("service", "dao", "grpc"),
            ),
        )

        assertTrue(yaml.contains("commands:"))
        assertTrue(yaml.contains("indent: \"api\""))
        assertTrue(yaml.contains("output_path: \"./gen/apis\""))
        assertTrue(yaml.contains("output_path: \"./gen/router\""))
        assertTrue(yaml.contains("output_path: \"./gen/client\""))
        assertTrue(yaml.contains("indent: \"state\""))
        assertTrue(yaml.contains("name: \"mount\""))
        assertTrue(yaml.contains("- service: \"service\""))
    }
}
