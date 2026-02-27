package com.github.spelensgud.gogenieintellijplugin.gogenie.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class GogenieConfigToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val component = runCatching {
            GogenieVisualConfigPanel(project).component
        }.getOrElse { e ->
            thisLogger().error("创建 gogenie 可视化配置面板失败", e)
            JPanel(BorderLayout()).apply {
                add(JBLabel("gogenie 配置面板加载失败: ${e.message ?: e.javaClass.simpleName}"), BorderLayout.CENTER)
            }
        }
        val content = ContentFactory.getInstance().createContent(component, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}

private class GogenieVisualConfigPanel(private val project: Project) {
    private val globalProjectName = textField()
    private val globalProjectModule = textField()
    private val globalProjectVersion = textField("1.0.0")
    private val globalProjectAuthor = textField()
    private val globalOutputBasePath = textField("./")
    private val globalOutputBackup = checkBox(true)
    private val globalOutputOverwrite = checkBox(false)
    private val globalOutputFormat = checkBox(true)
    private val globalTemplateBaseDir = textField(".gogenie/templates")
    private val globalTemplateSuffix = textField(".tmpl")
    private val globalExcludeDirs = textArea("vendor\ntestdata\n.git\nnode_modules")

    private val autowireEnabled = checkBox(true)
    private val autowireSearchPath = textField("./")
    private val autowireOutputPath = textField("./cmd/internal")
    private val autowirePackage = textField("internal")
    private val autowireInitTypes = textArea("*")
    private val autowireEnableCache = checkBox(true)
    private val autowireParallel = textField("0")
    private val autowireExcludeDirs = textArea("vendor\ntestdata\n.git\nnode_modules")
    private val autowireIncludeOnly = textArea("")
    private val autowireWatch = checkBox(false)
    private val autowireWatchIgnore = textArea("*.gen.go\nwire_gen.go")

    private val db2Enabled = checkBox(true)
    private val dbType = textField("postgresql")
    private val dbHost = textField("localhost")
    private val dbPort = textField("5432")
    private val dbUser = textField("postgres")
    private val dbPassword = textField("")
    private val dbDatabase = textField("")
    private val dbSchema = textField("public")
    private val dbTables = textArea("")
    private val dbCharset = textField("utf8mb4")
    private val dbSslMode = textField("disable")
    private val dbOutputPath = textField("./internal/table")
    private val dbOutputPackage = textField("table")
    private val dbGormAnnotation = checkBox(true)
    private val dbJsonTag = textField("snake")
    private val dbSqlTag = textField("gorm")
    private val dbCommentOutside = checkBox(true)
    private val dbSqlInfo = checkBox(false)
    private val dbTypeMap = textArea("interface{}=string")
    private val dbGenericTemplate = textField("model_generic")
    private val dbGenericMapTypes = textArea("int\nstring")
    private val dbGenerateCast = checkBox(true)
    private val dbCastTemplate = textField("model_cast")

    private val enumEnabled = checkBox(true)
    private val enumScope = textField("./")
    private val enumIndent = textField("enum")
    private val enumOutputPath = textField("./internal/enum")
    private val enumPackage = textField("enum")
    private val enumTemplate = textField("enum")

    private val httpEnabled = checkBox(true)
    private val httpIndent = textField("service")
    private val httpClientScope = textField("./")
    private val httpClientOutputPath = textField("./clients")
    private val httpClientTemplate = textField("http_client_api")
    private val httpClientBaseTemplate = textField("http_client_base")
    private val httpRouterScope = textField("./")
    private val httpRouterOutputPath = textField("./apis")
    private val httpRouterTemplate = textField("http_router")
    private val httpApiScope = textField("./")
    private val httpApiOutputPath = textField("./apis")
    private val httpApiTemplate = textField("http_api")
    private val httpSwaggerEnabled = checkBox(true)
    private val httpSwaggerPath = textField("docs/swagger.json")
    private val httpSwaggerMainApiPath = textField("./apis/root.go")
    private val httpSwaggerSuccess = textField("200 {object} object{data={{ .Response }},ok=bool}")
    private val httpSwaggerFailed = textField("400,500 {object} object{message=string,ok=bool,code=int} \"failed\"")
    private val httpSwaggerProduceType = textField("application/json")
    private val httpSwaggerTemplate = textField("http_swagger")

    private val implEnabled = checkBox(true)
    private val implScope = textField("./")
    private val implProtoScope = textField("./")
    private val implProtoPaths = textArea("proto")
    private val implIndentService = ImplIndentFields(
        service = textField("service"),
        outputPath = textField("./internal/svc_impls"),
        structName = textField("Service"),
        pkgPrefix = textField("svc"),
        template = textField("impl"),
        isGrpcService = checkBox(false),
        grpcSuffix = textField(""),
        enableRule = checkBox(true),
    )
    private val implIndentDao = ImplIndentFields(
        service = textField("dao"),
        outputPath = textField("./internal/dao_impls"),
        structName = textField("DaoImpl"),
        pkgPrefix = textField("dao"),
        template = textField("impl"),
        isGrpcService = checkBox(false),
        grpcSuffix = textField(""),
        enableRule = checkBox(true),
    )
    private val implIndentGrpc = ImplIndentFields(
        service = textField("grpc"),
        outputPath = textField("./internal/grpc_impls"),
        structName = textField("GrpcImpl"),
        pkgPrefix = textField("grpc"),
        template = textField("impl"),
        isGrpcService = checkBox(true),
        grpcSuffix = textField("Server"),
        enableRule = checkBox(true),
    )

    private val mountEnable = checkBox(true)
    private val mountScope = textField("./")
    private val mountName = textField("mount")
    private val mountArgs = textArea("")
    private val mountTag = textField("json")

    private val templateEnabled = checkBox(true)
    private val templateModelPath = textField("./internal/table")
    private val templateTemplateDir = textField(".gogenie/templates")
    private val templateOutputPrefix = textField("./")
    private val templateService = TemplateItemFields(
        name = textField("service"),
        template = textField(""),
        outputPath = textField("service/{{ .PackageName }}.go"),
        pkg = textField(""),
        overwrite = checkBox(false),
        enabled = checkBox(true),
    )
    private val templateDao = TemplateItemFields(
        name = textField("dao"),
        template = textField(""),
        outputPath = textField("dao/{{ .PackageName }}.go"),
        pkg = textField(""),
        overwrite = checkBox(false),
        enabled = checkBox(true),
    )
    private val templateServiceImpl = TemplateItemFields(
        name = textField("service_impl"),
        template = textField(""),
        outputPath = textField("internal/svc_impls/svc_{{.PackageName}}/{{ .PackageName }}.go"),
        pkg = textField(""),
        overwrite = checkBox(false),
        enabled = checkBox(true),
    )
    private val templateDaoImpl = TemplateItemFields(
        name = textField("dao_impl"),
        template = textField(""),
        outputPath = textField("internal/dao_impls/dao_{{.PackageName}}/{{ .PackageName }}.go"),
        pkg = textField(""),
        overwrite = checkBox(false),
        enabled = checkBox(true),
    )

    private val ruleEnabled = checkBox(true)
    private val ruleScope = textField("./")
    private val ruleConcurrency = textField("4")
    private val ruleMaxRetries = textField("3")
    private val ruleDryRun = checkBox(false)
    private val ruleVerbose = checkBox(false)
    private val ruleLlmProvider = textField("openai")
    private val ruleLlmBaseUrl = textField("https://api.openai.com/v1")
    private val ruleLlmApiKey = textField("")
    private val ruleLlmModel = textField("gpt-4o-mini")

    val component: JComponent

    init {
        val tabs = JBTabbedPane().apply {
            addTab("Global", scrollable(buildGlobalPanel()))
            addTab("Autowire", scrollable(buildAutowirePanel()))
            addTab("DB2Struct", scrollable(buildDb2StructPanel()))
            addTab("Enum", scrollable(buildEnumPanel()))
            addTab("HTTP", scrollable(buildHttpPanel()))
            addTab("Impl", scrollable(buildImplPanel()))
            addTab("Mount", scrollable(buildMountPanel()))
            addTab("Template", scrollable(buildTemplatePanel()))
            addTab("Rule", scrollable(buildRulePanel()))
        }

        val loadButton = actionButton("加载") { loadConfig() }
        val resetButton = actionButton("重置模板") { applyDefaults() }
        val saveButton = actionButton("保存", primary = true) { saveConfig() }

        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            border = JBUI.Borders.empty(8, 12, 10, 12)
            add(loadButton)
            add(resetButton)
            add(saveButton)
        }

        component = JPanel(BorderLayout()).apply {
            add(tabs, BorderLayout.CENTER)
            add(actionPanel, BorderLayout.SOUTH)
        }

        loadConfig()
    }

    private fun buildGlobalPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addComponent(sectionLabel("项目"))
            .addConfigField("global.project.name", globalProjectName)
            .addConfigField("global.project.module", globalProjectModule)
            .addConfigField("global.project.version", globalProjectVersion)
            .addConfigField("global.project.author", globalProjectAuthor)
            .addComponent(sectionLabel("输出"))
            .addConfigField("global.output.base_path", globalOutputBasePath)
            .addConfigField("global.output.backup", globalOutputBackup)
            .addConfigField("global.output.overwrite", globalOutputOverwrite)
            .addConfigField("global.output.format", globalOutputFormat)
            .addComponent(sectionLabel("模板"))
            .addConfigField("global.template.base_dir", globalTemplateBaseDir)
            .addConfigField("global.template.suffix", globalTemplateSuffix)
            .addComponent(sectionLabel("排除目录"))
            .addConfigField("global.exclude_dirs", areaScroll(globalExcludeDirs))
            .panel
    }

    private fun buildAutowirePanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.autowire.enabled", autowireEnabled)
            .addConfigField("commands.autowire.search_path", autowireSearchPath)
            .addConfigField("commands.autowire.output_path", autowireOutputPath)
            .addConfigField("commands.autowire.package", autowirePackage)
            .addConfigField("commands.autowire.init_types", areaScroll(autowireInitTypes))
            .addConfigField("commands.autowire.enable_cache", autowireEnableCache)
            .addConfigField("commands.autowire.parallel", autowireParallel)
            .addConfigField("commands.autowire.exclude_dirs", areaScroll(autowireExcludeDirs))
            .addConfigField("commands.autowire.include_only", areaScroll(autowireIncludeOnly))
            .addConfigField("commands.autowire.watch", autowireWatch)
            .addConfigField("commands.autowire.watch_ignore", areaScroll(autowireWatchIgnore))
            .panel
    }

    private fun buildDb2StructPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.db2struct.enabled", db2Enabled)
            .addComponent(sectionLabel("database"))
            .addConfigField("type", dbType)
            .addConfigField("host", dbHost)
            .addConfigField("port", dbPort)
            .addConfigField("user", dbUser)
            .addConfigField("password", dbPassword)
            .addConfigField("database", dbDatabase)
            .addConfigField("schema", dbSchema)
            .addConfigField("tables", areaScroll(dbTables))
            .addConfigField("charset", dbCharset)
            .addConfigField("ssl_mode", dbSslMode)
            .addComponent(sectionLabel("output"))
            .addConfigField("path", dbOutputPath)
            .addConfigField("package", dbOutputPackage)
            .addConfigField("gorm_annotation", dbGormAnnotation)
            .addConfigField("json_tag", dbJsonTag)
            .addConfigField("sql_tag", dbSqlTag)
            .addComponent(sectionLabel("options"))
            .addConfigField("comment_outside", dbCommentOutside)
            .addConfigField("sql_info", dbSqlInfo)
            .addConfigField("type_map（每行 key=value）", areaScroll(dbTypeMap))
            .addComponent(sectionLabel("泛型/转换"))
            .addConfigField("generic_template", dbGenericTemplate)
            .addConfigField("generic_map_types", areaScroll(dbGenericMapTypes))
            .addConfigField("generate_cast", dbGenerateCast)
            .addConfigField("cast_template", dbCastTemplate)
            .panel
    }

    private fun buildEnumPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.enum.enabled", enumEnabled)
            .addConfigField("commands.enum.scope", enumScope)
            .addConfigField("commands.enum.indent", enumIndent)
            .addConfigField("commands.enum.output_path", enumOutputPath)
            .addConfigField("commands.enum.package", enumPackage)
            .addConfigField("commands.enum.template", enumTemplate)
            .panel
    }

    private fun buildHttpPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.http.enabled", httpEnabled)
            .addConfigField("commands.http.indent", httpIndent)
            .addComponent(sectionLabel("client"))
            .addConfigField("scope", httpClientScope)
            .addConfigField("output_path", httpClientOutputPath)
            .addConfigField("template", httpClientTemplate)
            .addConfigField("base_template", httpClientBaseTemplate)
            .addComponent(sectionLabel("router"))
            .addConfigField("scope", httpRouterScope)
            .addConfigField("output_path", httpRouterOutputPath)
            .addConfigField("template", httpRouterTemplate)
            .addComponent(sectionLabel("api"))
            .addConfigField("scope", httpApiScope)
            .addConfigField("output_path", httpApiOutputPath)
            .addConfigField("template", httpApiTemplate)
            .addComponent(sectionLabel("swagger"))
            .addConfigField("enabled", httpSwaggerEnabled)
            .addConfigField("path", httpSwaggerPath)
            .addConfigField("mainApiPath", httpSwaggerMainApiPath)
            .addConfigField("success", httpSwaggerSuccess)
            .addConfigField("failed", httpSwaggerFailed)
            .addConfigField("produceType", httpSwaggerProduceType)
            .addConfigField("template", httpSwaggerTemplate)
            .panel
    }

    private fun buildImplPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.impl.enabled", implEnabled)
            .addConfigField("commands.impl.scope", implScope)
            .addConfigField("commands.impl.proto_scope", implProtoScope)
            .addConfigField("commands.impl.proto_paths", areaScroll(implProtoPaths))
            .addComponent(sectionLabel("indents[0] service"))
            .also { appendImplIndentFields(it, implIndentService) }
            .addComponent(sectionLabel("indents[1] dao"))
            .also { appendImplIndentFields(it, implIndentDao) }
            .addComponent(sectionLabel("indents[2] grpc"))
            .also { appendImplIndentFields(it, implIndentGrpc) }
            .panel
    }

    private fun buildMountPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.mount.enable", mountEnable)
            .addConfigField("commands.mount.scope", mountScope)
            .addConfigField("commands.mount.name", mountName)
            .addConfigField("commands.mount.args", areaScroll(mountArgs))
            .addConfigField("commands.mount.tag", mountTag)
            .panel
    }

    private fun buildTemplatePanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.template.enabled", templateEnabled)
            .addConfigField("commands.template.model_path", templateModelPath)
            .addConfigField("commands.template.template_dir", templateTemplateDir)
            .addConfigField("commands.template.output_prefix", templateOutputPrefix)
            .addComponent(sectionLabel("templates[0]"))
            .also { appendTemplateItemFields(it, templateService) }
            .addComponent(sectionLabel("templates[1]"))
            .also { appendTemplateItemFields(it, templateDao) }
            .addComponent(sectionLabel("templates[2]"))
            .also { appendTemplateItemFields(it, templateServiceImpl) }
            .addComponent(sectionLabel("templates[3]"))
            .also { appendTemplateItemFields(it, templateDaoImpl) }
            .panel
    }

    private fun buildRulePanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addConfigField("commands.rule.enabled", ruleEnabled)
            .addConfigField("commands.rule.scope", ruleScope)
            .addConfigField("commands.rule.concurrency", ruleConcurrency)
            .addConfigField("commands.rule.max_retries", ruleMaxRetries)
            .addConfigField("commands.rule.dry_run", ruleDryRun)
            .addConfigField("commands.rule.verbose", ruleVerbose)
            .addComponent(sectionLabel("llm"))
            .addConfigField("provider", ruleLlmProvider)
            .addConfigField("base_url", ruleLlmBaseUrl)
            .addConfigField("api_key", ruleLlmApiKey)
            .addConfigField("model", ruleLlmModel)
            .panel
    }

    private fun appendImplIndentFields(builder: FormBuilder, fields: ImplIndentFields) {
        builder
            .addConfigField("service", fields.service)
            .addConfigField("output_path", fields.outputPath)
            .addConfigField("structName", fields.structName)
            .addConfigField("pkgPrefix", fields.pkgPrefix)
            .addConfigField("template", fields.template)
            .addConfigField("is_grpc_service", fields.isGrpcService)
            .addConfigField("grpc_suffix", fields.grpcSuffix)
            .addConfigField("enable_rule", fields.enableRule)
    }

    private fun appendTemplateItemFields(builder: FormBuilder, fields: TemplateItemFields) {
        builder
            .addConfigField("name", fields.name)
            .addConfigField("template", fields.template)
            .addConfigField("output_path", fields.outputPath)
            .addConfigField("package", fields.pkg)
            .addConfigField("overwrite", fields.overwrite)
            .addConfigField("enabled", fields.enabled)
    }

    private fun scrollable(panel: JPanel): JBScrollPane {
        val container = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 0, 8)
            add(panel, BorderLayout.NORTH)
        }
        return JBScrollPane(container).apply {
            border = null
        }
    }

    private fun areaScroll(area: JBTextArea): JBScrollPane {
        area.lineWrap = false
        area.wrapStyleWord = false
        area.margin = JBUI.insets(6, 8)
        area.border = JBUI.Borders.empty()
        return JBScrollPane(area).apply {
            preferredSize = Dimension(280, 90)
        }
    }

    private fun actionButton(text: String, primary: Boolean = false, onClick: () -> Unit): JButton {
        return JButton(text).apply {
            preferredSize = Dimension(84, 30)
            minimumSize = preferredSize
            putClientProperty("JButton.buttonType", if (primary) "default" else "outline")
            addActionListener { onClick() }
        }
    }

    private fun sectionLabel(text: String): JBLabel {
        return JBLabel(text).apply {
            font = font.deriveFont(Font.BOLD)
            border = BorderFactory.createEmptyBorder(6, 0, 2, 0)
            foreground = JBColor.foreground()
        }
    }

    private fun FormBuilder.addConfigField(rawLabel: String, component: JComponent): FormBuilder {
        return addLabeledComponent(compactLabel(rawLabel), component, 1, false)
    }

    private fun compactLabel(rawLabel: String): String {
        val suffixStart = rawLabel.indexOf('（').takeIf { it >= 0 } ?: rawLabel.length
        val base = rawLabel.substring(0, suffixStart)
        val suffix = rawLabel.substring(suffixStart)
        return base.substringAfterLast('.') + suffix
    }

    private fun configPath(): Path? {
        val basePath = project.basePath ?: return null
        return Paths.get(basePath).resolve(".gogenie").resolve("config.yaml")
    }

    private fun loadConfig() {
        val path = configPath()
        if (path == null) {
            applyDefaults()
            return
        }

        if (!Files.exists(path)) {
            applyDefaults()
            return
        }

        val root = runCatching { parseYamlToMap(Files.readString(path)) }
            .getOrElse { e ->
                applyDefaults()
                notify(NotificationType.ERROR, "gogenie 配置解析失败", e.message ?: e.javaClass.simpleName)
                return
            }
        applyMapToFields(root)
    }

    private fun saveConfig() {
        val path = configPath()
        if (path == null) {
            notify(NotificationType.ERROR, "保存失败", "未找到项目根路径")
            return
        }

        val root = buildYamlMapFromFields()
        val yamlText = dumpYaml(root)
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(path, yamlText)
        }.onFailure { e ->
            notify(NotificationType.ERROR, "gogenie 配置保存失败", e.message ?: e.javaClass.simpleName)
            return
        }

        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        VirtualFileManager.getInstance().asyncRefresh(null)
        notify(NotificationType.INFORMATION, "gogenie 配置已保存", path.toString())
    }

    private fun applyDefaults() {
        applyMapToFields(parseYamlToMap(GogenieConfigTemplate.fullTemplate()))
    }

    private fun applyMapToFields(root: MutableMap<String, Any?>) {
        setText(globalProjectName, getString(root, "global", "project", "name"))
        setText(globalProjectModule, getString(root, "global", "project", "module"))
        setText(globalProjectVersion, getString(root, "global", "project", "version", default = "1.0.0"))
        setText(globalProjectAuthor, getString(root, "global", "project", "author"))
        setText(globalOutputBasePath, getString(root, "global", "output", "base_path", default = "./"))
        setChecked(globalOutputBackup, getBoolean(root, "global", "output", "backup", default = true))
        setChecked(globalOutputOverwrite, getBoolean(root, "global", "output", "overwrite", default = false))
        setChecked(globalOutputFormat, getBoolean(root, "global", "output", "format", default = true))
        setText(globalTemplateBaseDir, getString(root, "global", "template", "base_dir", default = ".gogenie/templates"))
        setText(globalTemplateSuffix, getString(root, "global", "template", "suffix", default = ".tmpl"))
        setArea(globalExcludeDirs, getStringList(root, "global", "exclude_dirs", default = listOf("vendor", "testdata", ".git", "node_modules")))

        setChecked(autowireEnabled, getBoolean(root, "commands", "autowire", "enabled", default = true))
        setText(autowireSearchPath, getString(root, "commands", "autowire", "search_path", default = "./"))
        setText(autowireOutputPath, getString(root, "commands", "autowire", "output_path", default = "./cmd/internal"))
        setText(autowirePackage, getString(root, "commands", "autowire", "package", default = "internal"))
        setArea(autowireInitTypes, getStringList(root, "commands", "autowire", "init_types", default = listOf("*")))
        setChecked(autowireEnableCache, getBoolean(root, "commands", "autowire", "enable_cache", default = true))
        setText(autowireParallel, getInt(root, "commands", "autowire", "parallel", default = 0).toString())
        setArea(autowireExcludeDirs, getStringList(root, "commands", "autowire", "exclude_dirs", default = listOf("vendor", "testdata", ".git", "node_modules")))
        setArea(autowireIncludeOnly, getStringList(root, "commands", "autowire", "include_only", default = emptyList()))
        setChecked(autowireWatch, getBoolean(root, "commands", "autowire", "watch", default = false))
        setArea(autowireWatchIgnore, getStringList(root, "commands", "autowire", "watch_ignore", default = listOf("*.gen.go", "wire_gen.go")))

        setChecked(db2Enabled, getBoolean(root, "commands", "db2struct", "enabled", default = true))
        setText(dbType, getString(root, "commands", "db2struct", "database", "type", default = "postgresql"))
        setText(dbHost, getString(root, "commands", "db2struct", "database", "host", default = "localhost"))
        setText(dbPort, getInt(root, "commands", "db2struct", "database", "port", default = 5432).toString())
        setText(dbUser, getString(root, "commands", "db2struct", "database", "user", default = "postgres"))
        setText(dbPassword, getString(root, "commands", "db2struct", "database", "password"))
        setText(dbDatabase, getString(root, "commands", "db2struct", "database", "database"))
        setText(dbSchema, getString(root, "commands", "db2struct", "database", "schema", default = "public"))
        setArea(dbTables, getStringList(root, "commands", "db2struct", "database", "tables", default = emptyList()))
        setText(dbCharset, getString(root, "commands", "db2struct", "database", "charset", default = "utf8mb4"))
        setText(dbSslMode, getString(root, "commands", "db2struct", "database", "ssl_mode", default = "disable"))
        setText(dbOutputPath, getString(root, "commands", "db2struct", "output", "path", default = "./internal/table"))
        setText(dbOutputPackage, getString(root, "commands", "db2struct", "output", "package", default = "table"))
        setChecked(dbGormAnnotation, getBoolean(root, "commands", "db2struct", "output", "gorm_annotation", default = true))
        setText(dbJsonTag, getString(root, "commands", "db2struct", "output", "json_tag", default = "snake"))
        setText(dbSqlTag, getString(root, "commands", "db2struct", "output", "sql_tag", default = "gorm"))
        setChecked(dbCommentOutside, getBoolean(root, "commands", "db2struct", "options", "comment_outside", default = true))
        setChecked(dbSqlInfo, getBoolean(root, "commands", "db2struct", "options", "sql_info", default = false))
        setArea(dbTypeMap, getTypeMapLines(root))
        setText(dbGenericTemplate, getString(root, "commands", "db2struct", "generic_template", default = "model_generic"))
        setArea(dbGenericMapTypes, getStringList(root, "commands", "db2struct", "generic_map_types", default = listOf("int", "string")))
        setChecked(dbGenerateCast, getBoolean(root, "commands", "db2struct", "generate_cast", default = true))
        setText(dbCastTemplate, getString(root, "commands", "db2struct", "cast_template", default = "model_cast"))

        setChecked(enumEnabled, getBoolean(root, "commands", "enum", "enabled", default = true))
        setText(enumScope, getString(root, "commands", "enum", "scope", default = "./"))
        setText(enumIndent, getString(root, "commands", "enum", "indent", default = "enum"))
        setText(enumOutputPath, getString(root, "commands", "enum", "output_path", default = "./internal/enum"))
        setText(enumPackage, getString(root, "commands", "enum", "package", default = "enum"))
        setText(enumTemplate, getString(root, "commands", "enum", "template", default = "enum"))

        setChecked(httpEnabled, getBoolean(root, "commands", "http", "enabled", default = true))
        setText(httpIndent, getString(root, "commands", "http", "indent", default = "service"))
        setText(httpClientScope, getString(root, "commands", "http", "client", "scope", default = "./"))
        setText(httpClientOutputPath, getString(root, "commands", "http", "client", "output_path", default = "./clients"))
        setText(httpClientTemplate, getString(root, "commands", "http", "client", "template", default = "http_client_api"))
        setText(httpClientBaseTemplate, getString(root, "commands", "http", "client", "base_template", default = "http_client_base"))
        setText(httpRouterScope, getString(root, "commands", "http", "router", "scope", default = "./"))
        setText(httpRouterOutputPath, getString(root, "commands", "http", "router", "output_path", default = "./apis"))
        setText(httpRouterTemplate, getString(root, "commands", "http", "router", "template", default = "http_router"))
        setText(httpApiScope, getString(root, "commands", "http", "api", "scope", default = "./"))
        setText(httpApiOutputPath, getString(root, "commands", "http", "api", "output_path", default = "./apis"))
        setText(httpApiTemplate, getString(root, "commands", "http", "api", "template", default = "http_api"))
        setChecked(httpSwaggerEnabled, getBoolean(root, "commands", "http", "swagger", "enabled", default = true))
        setText(httpSwaggerPath, getString(root, "commands", "http", "swagger", "path", default = "docs/swagger.json"))
        setText(httpSwaggerMainApiPath, getString(root, "commands", "http", "swagger", "mainApiPath", default = "./apis/root.go"))
        setText(httpSwaggerSuccess, getString(root, "commands", "http", "swagger", "success", default = "200 {object} object{data={{ .Response }},ok=bool}"))
        setText(httpSwaggerFailed, getString(root, "commands", "http", "swagger", "failed", default = "400,500 {object} object{message=string,ok=bool,code=int} \"failed\""))
        setText(httpSwaggerProduceType, getString(root, "commands", "http", "swagger", "produceType", default = "application/json"))
        setText(httpSwaggerTemplate, getString(root, "commands", "http", "swagger", "template", default = "http_swagger"))

        setChecked(implEnabled, getBoolean(root, "commands", "impl", "enabled", default = true))
        setText(implScope, getString(root, "commands", "impl", "scope", default = "./"))
        setText(implProtoScope, getString(root, "commands", "impl", "proto_scope", default = "./"))
        setArea(implProtoPaths, getStringList(root, "commands", "impl", "proto_paths", default = listOf("proto")))
        applyIndentFields(getListMap(root, listOf("commands", "impl", "indents"), 0), implIndentService)
        applyIndentFields(getListMap(root, listOf("commands", "impl", "indents"), 1), implIndentDao)
        applyIndentFields(getListMap(root, listOf("commands", "impl", "indents"), 2), implIndentGrpc)

        setChecked(mountEnable, getBoolean(root, "commands", "mount", "enable", default = true))
        setText(mountScope, getString(root, "commands", "mount", "scope", default = "./"))
        setText(mountName, getString(root, "commands", "mount", "name", default = "mount"))
        setArea(mountArgs, getStringList(root, "commands", "mount", "args", default = emptyList()))
        setText(mountTag, getString(root, "commands", "mount", "tag", default = "json"))

        setChecked(templateEnabled, getBoolean(root, "commands", "template", "enabled", default = true))
        setText(templateModelPath, getString(root, "commands", "template", "model_path", default = "./internal/table"))
        setText(templateTemplateDir, getString(root, "commands", "template", "template_dir", default = ".gogenie/templates"))
        setText(templateOutputPrefix, getString(root, "commands", "template", "output_prefix", default = "./"))
        applyTemplateFields(getListMap(root, listOf("commands", "template", "templates"), 0), templateService)
        applyTemplateFields(getListMap(root, listOf("commands", "template", "templates"), 1), templateDao)
        applyTemplateFields(getListMap(root, listOf("commands", "template", "templates"), 2), templateServiceImpl)
        applyTemplateFields(getListMap(root, listOf("commands", "template", "templates"), 3), templateDaoImpl)

        setChecked(ruleEnabled, getBoolean(root, "commands", "rule", "enabled", default = true))
        setText(ruleScope, getString(root, "commands", "rule", "scope", default = "./"))
        setText(ruleConcurrency, getInt(root, "commands", "rule", "concurrency", default = 4).toString())
        setText(ruleMaxRetries, getInt(root, "commands", "rule", "max_retries", default = 3).toString())
        setChecked(ruleDryRun, getBoolean(root, "commands", "rule", "dry_run", default = false))
        setChecked(ruleVerbose, getBoolean(root, "commands", "rule", "verbose", default = false))
        setText(ruleLlmProvider, getString(root, "commands", "rule", "llm", "provider", default = "openai"))
        setText(ruleLlmBaseUrl, getString(root, "commands", "rule", "llm", "base_url", default = "https://api.openai.com/v1"))
        setText(ruleLlmApiKey, getString(root, "commands", "rule", "llm", "api_key"))
        setText(ruleLlmModel, getString(root, "commands", "rule", "llm", "model", default = "gpt-4o-mini"))
    }

    private fun buildYamlMapFromFields(): MutableMap<String, Any?> {
        val root = linkedMapOf<String, Any?>()

        root["global"] = linkedMapOf(
            "project" to linkedMapOf(
                "name" to globalProjectName.text.trim(),
                "module" to globalProjectModule.text.trim(),
                "version" to globalProjectVersion.text.trim(),
                "author" to globalProjectAuthor.text.trim(),
            ),
            "output" to linkedMapOf(
                "base_path" to globalOutputBasePath.text.trim(),
                "backup" to globalOutputBackup.isSelected,
                "overwrite" to globalOutputOverwrite.isSelected,
                "format" to globalOutputFormat.isSelected,
            ),
            "template" to linkedMapOf(
                "base_dir" to globalTemplateBaseDir.text.trim(),
                "suffix" to globalTemplateSuffix.text.trim(),
            ),
            "exclude_dirs" to lines(globalExcludeDirs),
        )

        root["commands"] = linkedMapOf(
            "autowire" to linkedMapOf(
                "enabled" to autowireEnabled.isSelected,
                "search_path" to autowireSearchPath.text.trim(),
                "output_path" to autowireOutputPath.text.trim(),
                "package" to autowirePackage.text.trim(),
                "init_types" to lines(autowireInitTypes, listOf("*")),
                "enable_cache" to autowireEnableCache.isSelected,
                "parallel" to parseInt(autowireParallel, 0),
                "exclude_dirs" to lines(autowireExcludeDirs),
                "include_only" to lines(autowireIncludeOnly),
                "watch" to autowireWatch.isSelected,
                "watch_ignore" to lines(autowireWatchIgnore),
            ),
            "db2struct" to linkedMapOf(
                "enabled" to db2Enabled.isSelected,
                "database" to linkedMapOf(
                    "type" to dbType.text.trim(),
                    "host" to dbHost.text.trim(),
                    "port" to parseInt(dbPort, 5432),
                    "user" to dbUser.text.trim(),
                    "password" to dbPassword.text,
                    "database" to dbDatabase.text.trim(),
                    "schema" to dbSchema.text.trim(),
                    "tables" to lines(dbTables),
                    "charset" to dbCharset.text.trim(),
                    "ssl_mode" to dbSslMode.text.trim(),
                ),
                "output" to linkedMapOf(
                    "path" to dbOutputPath.text.trim(),
                    "package" to dbOutputPackage.text.trim(),
                    "gorm_annotation" to dbGormAnnotation.isSelected,
                    "json_tag" to dbJsonTag.text.trim(),
                    "sql_tag" to dbSqlTag.text.trim(),
                ),
                "options" to linkedMapOf(
                    "comment_outside" to dbCommentOutside.isSelected,
                    "sql_info" to dbSqlInfo.isSelected,
                    "type_map" to parseTypeMap(dbTypeMap),
                ),
                "generic_template" to dbGenericTemplate.text.trim(),
                "generic_map_types" to lines(dbGenericMapTypes, listOf("int", "string")),
                "generate_cast" to dbGenerateCast.isSelected,
                "cast_template" to dbCastTemplate.text.trim(),
            ),
            "enum" to linkedMapOf(
                "enabled" to enumEnabled.isSelected,
                "scope" to enumScope.text.trim(),
                "indent" to enumIndent.text.trim(),
                "output_path" to enumOutputPath.text.trim(),
                "package" to enumPackage.text.trim(),
                "template" to enumTemplate.text.trim(),
            ),
            "http" to linkedMapOf(
                "enabled" to httpEnabled.isSelected,
                "indent" to httpIndent.text.trim(),
                "client" to linkedMapOf(
                    "scope" to httpClientScope.text.trim(),
                    "output_path" to httpClientOutputPath.text.trim(),
                    "template" to httpClientTemplate.text.trim(),
                    "base_template" to httpClientBaseTemplate.text.trim(),
                ),
                "router" to linkedMapOf(
                    "scope" to httpRouterScope.text.trim(),
                    "output_path" to httpRouterOutputPath.text.trim(),
                    "template" to httpRouterTemplate.text.trim(),
                ),
                "api" to linkedMapOf(
                    "scope" to httpApiScope.text.trim(),
                    "output_path" to httpApiOutputPath.text.trim(),
                    "template" to httpApiTemplate.text.trim(),
                ),
                "swagger" to linkedMapOf(
                    "enabled" to httpSwaggerEnabled.isSelected,
                    "path" to httpSwaggerPath.text.trim(),
                    "mainApiPath" to httpSwaggerMainApiPath.text.trim(),
                    "success" to httpSwaggerSuccess.text.trim(),
                    "failed" to httpSwaggerFailed.text.trim(),
                    "produceType" to httpSwaggerProduceType.text.trim(),
                    "template" to httpSwaggerTemplate.text.trim(),
                ),
            ),
            "impl" to linkedMapOf(
                "enabled" to implEnabled.isSelected,
                "scope" to implScope.text.trim(),
                "indents" to listOf(
                    indentMap(implIndentService),
                    indentMap(implIndentDao),
                    indentMap(implIndentGrpc),
                ),
                "proto_scope" to implProtoScope.text.trim(),
                "proto_paths" to lines(implProtoPaths, listOf("proto")),
            ),
            "mount" to linkedMapOf(
                "enable" to mountEnable.isSelected,
                "scope" to mountScope.text.trim(),
                "name" to mountName.text.trim(),
                "args" to lines(mountArgs),
                "tag" to mountTag.text.trim(),
            ),
            "template" to linkedMapOf(
                "enabled" to templateEnabled.isSelected,
                "model_path" to templateModelPath.text.trim(),
                "template_dir" to templateTemplateDir.text.trim(),
                "output_prefix" to templateOutputPrefix.text.trim(),
                "templates" to listOf(
                    templateMap(templateService),
                    templateMap(templateDao),
                    templateMap(templateServiceImpl),
                    templateMap(templateDaoImpl),
                ),
            ),
            "rule" to linkedMapOf(
                "enabled" to ruleEnabled.isSelected,
                "scope" to ruleScope.text.trim(),
                "concurrency" to parseInt(ruleConcurrency, 4),
                "max_retries" to parseInt(ruleMaxRetries, 3),
                "dry_run" to ruleDryRun.isSelected,
                "verbose" to ruleVerbose.isSelected,
                "llm" to linkedMapOf(
                    "provider" to ruleLlmProvider.text.trim(),
                    "base_url" to ruleLlmBaseUrl.text.trim(),
                    "api_key" to ruleLlmApiKey.text,
                    "model" to ruleLlmModel.text.trim(),
                ),
            ),
        )
        return root
    }

    private fun indentMap(fields: ImplIndentFields): Map<String, Any?> {
        return linkedMapOf(
            "service" to fields.service.text.trim(),
            "output_path" to fields.outputPath.text.trim(),
            "structName" to fields.structName.text.trim(),
            "pkgPrefix" to fields.pkgPrefix.text.trim(),
            "template" to fields.template.text.trim(),
            "is_grpc_service" to fields.isGrpcService.isSelected,
            "grpc_suffix" to fields.grpcSuffix.text.trim(),
            "enable_rule" to fields.enableRule.isSelected,
        )
    }

    private fun templateMap(fields: TemplateItemFields): Map<String, Any?> {
        return linkedMapOf(
            "name" to fields.name.text.trim(),
            "template" to fields.template.text,
            "output_path" to fields.outputPath.text.trim(),
            "package" to fields.pkg.text.trim(),
            "overwrite" to fields.overwrite.isSelected,
            "enabled" to fields.enabled.isSelected,
        )
    }

    private fun applyIndentFields(data: MutableMap<String, Any?>, fields: ImplIndentFields) {
        setText(fields.service, mapString(data, "service"))
        setText(fields.outputPath, mapString(data, "output_path"))
        setText(fields.structName, mapString(data, "structName"))
        setText(fields.pkgPrefix, mapString(data, "pkgPrefix"))
        setText(fields.template, mapString(data, "template"))
        setChecked(fields.isGrpcService, mapBool(data, "is_grpc_service"))
        setText(fields.grpcSuffix, mapString(data, "grpc_suffix"))
        setChecked(fields.enableRule, mapBool(data, "enable_rule", true))
    }

    private fun applyTemplateFields(data: MutableMap<String, Any?>, fields: TemplateItemFields) {
        setText(fields.name, mapString(data, "name"))
        setText(fields.template, mapString(data, "template"))
        setText(fields.outputPath, mapString(data, "output_path"))
        setText(fields.pkg, mapString(data, "package"))
        setChecked(fields.overwrite, mapBool(data, "overwrite"))
        setChecked(fields.enabled, mapBool(data, "enabled", true))
    }

    private fun parseYamlToMap(text: String): MutableMap<String, Any?> {
        val loaded = Yaml().load<Any?>(text) ?: linkedMapOf<String, Any?>()
        return normalizeMap(loaded)
    }

    private fun normalizeMap(value: Any?): MutableMap<String, Any?> {
        if (value !is Map<*, *>) {
            return linkedMapOf()
        }
        val map = linkedMapOf<String, Any?>()
        for ((k, v) in value) {
            val key = k?.toString() ?: continue
            map[key] = normalizeValue(v)
        }
        return map
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeMap(value)
            is List<*> -> value.map { normalizeValue(it) }
            else -> value
        }
    }

    private fun dumpYaml(root: Map<String, Any?>): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            setPrettyFlow(true)
            indent = 2
            // SnakeYAML 要求 indicatorIndent 必须小于 indent。
            indicatorIndent = 1
            setSplitLines(false)
        }
        return Yaml(options).dump(root)
    }

    private fun getString(root: MutableMap<String, Any?>, vararg path: String, default: String = ""): String {
        val value = navigate(root, path.toList()) ?: return default
        return when (value) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> default
        }
    }

    private fun getBoolean(root: MutableMap<String, Any?>, vararg path: String, default: Boolean): Boolean {
        val value = navigate(root, path.toList()) ?: return default
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> default
        }
    }

    private fun getInt(root: MutableMap<String, Any?>, vararg path: String, default: Int): Int {
        val value = navigate(root, path.toList()) ?: return default
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun getStringList(
        root: MutableMap<String, Any?>,
        vararg path: String,
        default: List<String>,
    ): List<String> {
        val value = navigate(root, path.toList()) ?: return default
        val list = value as? List<*> ?: return default
        return list.mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
    }

    private fun getTypeMapLines(root: MutableMap<String, Any?>): List<String> {
        val value = navigate(root, listOf("commands", "db2struct", "options", "type_map"))
        val map = value as? Map<*, *> ?: return listOf("interface{}=string")
        return map.entries.mapNotNull { (k, v) ->
            val key = k?.toString()?.trim().orEmpty()
            if (key.isBlank()) null else "$key=${v?.toString().orEmpty()}"
        }
    }

    private fun getListMap(root: MutableMap<String, Any?>, path: List<String>, index: Int): MutableMap<String, Any?> {
        val value = navigate(root, path) as? List<*> ?: return linkedMapOf()
        val map = value.getOrNull(index) as? Map<*, *> ?: return linkedMapOf()
        return normalizeMap(map)
    }

    private fun navigate(root: MutableMap<String, Any?>, path: List<String>): Any? {
        var current: Any? = root
        for (key in path) {
            val asMap = current as? Map<*, *> ?: return null
            current = asMap[key]
        }
        return current
    }

    private fun mapString(map: MutableMap<String, Any?>, key: String, default: String = ""): String {
        val value = map[key] ?: return default
        return value.toString()
    }

    private fun mapBool(map: MutableMap<String, Any?>, key: String, default: Boolean = false): Boolean {
        val value = map[key] ?: return default
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    private fun setText(field: JBTextField, value: String) {
        field.text = value
    }

    private fun setChecked(box: JBCheckBox, value: Boolean) {
        box.isSelected = value
    }

    private fun setArea(area: JBTextArea, values: List<String>) {
        area.text = values.joinToString("\n")
    }

    private fun lines(area: JBTextArea, default: List<String> = emptyList()): List<String> {
        val values = area.text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        // 返回新实例，避免 SnakeYAML 因共享引用输出 &id001 锚点。
        return if (values.isEmpty()) ArrayList(default) else ArrayList(values)
    }

    private fun parseInt(field: JBTextField, default: Int): Int = field.text.trim().toIntOrNull() ?: default

    private fun parseTypeMap(area: JBTextArea): Map<String, String> {
        val ret = linkedMapOf<String, String>()
        for (line in area.text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                continue
            }
            val index = trimmed.indexOf('=').takeIf { it >= 0 } ?: trimmed.indexOf(':')
            if (index <= 0) {
                continue
            }
            val key = trimmed.substring(0, index).trim()
            val value = trimmed.substring(index + 1).trim()
            if (key.isNotBlank()) {
                ret[key] = value
            }
        }
        return ret
    }

    private fun notify(type: NotificationType, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("gogenie.impl")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun textField(default: String = ""): JBTextField = JBTextField(default)

    private fun textArea(default: String = ""): JBTextArea = JBTextArea(default)

    private fun checkBox(default: Boolean): JBCheckBox = JBCheckBox().apply { isSelected = default }

    private data class ImplIndentFields(
        val service: JBTextField,
        val outputPath: JBTextField,
        val structName: JBTextField,
        val pkgPrefix: JBTextField,
        val template: JBTextField,
        val isGrpcService: JBCheckBox,
        val grpcSuffix: JBTextField,
        val enableRule: JBCheckBox,
    )

    private data class TemplateItemFields(
        val name: JBTextField,
        val template: JBTextField,
        val outputPath: JBTextField,
        val pkg: JBTextField,
        val overwrite: JBCheckBox,
        val enabled: JBCheckBox,
    )
}
