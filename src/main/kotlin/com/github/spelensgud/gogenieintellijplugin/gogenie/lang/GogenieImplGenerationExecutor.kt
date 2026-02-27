package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object GogenieImplGenerationExecutor {
    fun generate(
        project: Project,
        sourceFilePath: String,
        annotationName: String,
        interfaceName: String,
    ) {
        val moduleDir = findGoModuleDir(sourceFilePath)
        if (moduleDir == null) {
            notify(
                project,
                NotificationType.ERROR,
                "未找到 go.mod，无法执行 gogenie impl",
                "请在 Go 模块项目中使用该功能",
            )
            return
        }

        object : Task.Backgroundable(project, "gogenie: 生成 $interfaceName 实现", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "执行 gogenie impl"
                saveAllDocumentsOnEdt()

                val (executable, prefixArgs) = resolveCommand(project)
                val sourceDir = runCatching { Paths.get(sourceFilePath).toAbsolutePath().parent }
                    .getOrNull()
                    ?.toString()
                    ?: moduleDir.toString()
                val scopeCandidates = linkedSetOf(sourceDir, moduleDir.toString())

                var finalResult: ProcessOutput? = null
                var finalScope = sourceDir
                var widenedScope = false
                var downgradedNoName = false
                var usedNameFilter = true

                for (scope in scopeCandidates) {
                    finalScope = scope
                    val resultWithName = runImpl(
                        executable = executable,
                        prefixArgs = prefixArgs,
                        workDir = moduleDir,
                        annotationName = annotationName,
                        interfaceName = interfaceName,
                        scope = scope,
                        withName = true,
                    )
                    if (resultWithName == null) {
                        notify(
                            project,
                            NotificationType.ERROR,
                            "执行 gogenie impl 失败",
                            "命令启动失败，请检查 gogenie 是否可执行",
                        )
                        return
                    }
                    finalResult = resultWithName
                    if (resultWithName.exitCode == 0) {
                        break
                    }

                    if (isNameFlagUnsupported(resultWithName)) {
                        downgradedNoName = true
                        usedNameFilter = false
                        val resultWithoutName = runImpl(
                            executable = executable,
                            prefixArgs = prefixArgs,
                            workDir = moduleDir,
                            annotationName = annotationName,
                            interfaceName = interfaceName,
                            scope = scope,
                            withName = false,
                        )
                        if (resultWithoutName == null) {
                            notify(
                                project,
                                NotificationType.ERROR,
                                "执行 gogenie impl 失败",
                                "命令启动失败，请检查 gogenie 是否可执行",
                            )
                            return
                        }
                        finalResult = resultWithoutName
                        if (resultWithoutName.exitCode == 0) {
                            break
                        }
                    }

                    if (!isNoAnnotationError(finalResult) || scope == moduleDir.toString()) {
                        break
                    }
                    widenedScope = true
                }

                val result = finalResult
                if (result == null) {
                    notify(
                        project,
                        NotificationType.ERROR,
                        "执行 gogenie impl 失败",
                        "未获取到命令执行结果",
                    )
                    return
                }

                if (result.exitCode == 0) {
                    VirtualFileManager.getInstance().asyncRefresh(null)
                    val stdout = result.stdout.trim().takeIf { it.isNotEmpty() } ?: "已生成 $interfaceName 的实现"
                    val tips = mutableListOf<String>()
                    if (usedNameFilter) {
                        tips += "仅生成接口: $interfaceName"
                    }
                    if (widenedScope) {
                        tips += "已自动扩大搜索范围到: $finalScope"
                    }
                    if (downgradedNoName) {
                        tips += "当前 gogenie 不支持 --name，已降级为范围生成。"
                    }
                    val tail = if (tips.isEmpty()) "" else "\n\n" + tips.joinToString("\n")
                    notify(project, NotificationType.INFORMATION, "gogenie impl 执行成功", stdout + tail)
                } else {
                    val stderr = result.stderr.trim().ifBlank { result.stdout.trim() }.ifBlank { "未知错误" }
                    notify(
                        project,
                        NotificationType.ERROR,
                        "gogenie impl 执行失败（exit=${result.exitCode}）",
                        stderr,
                    )
                }
            }
        }.queue()
    }

    fun runQuickCommand(
        project: Project,
        sourceFilePath: String,
        annotationName: String,
        command: GogenieQuickCommandSpec,
    ) {
        val moduleDir = findGoModuleDir(sourceFilePath)
        if (moduleDir == null) {
            notify(
                project,
                NotificationType.ERROR,
                "未找到 go.mod，无法执行 gogenie ${command.commandLabel}",
                "请在 Go 模块项目中使用该功能",
            )
            return
        }

        object : Task.Backgroundable(project, "gogenie: 执行 ${command.commandLabel}", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "执行 gogenie ${command.commandLabel}"
                saveAllDocumentsOnEdt()

                val sourceDir = runCatching { Paths.get(sourceFilePath).toAbsolutePath().parent }
                    .getOrNull()
                    ?.toString()
                    ?: moduleDir.toString()
                val scope = when (command.scopeTarget) {
                    GogenieQuickCommandSpec.ScopeTarget.NONE -> null
                    GogenieQuickCommandSpec.ScopeTarget.FILE -> sourceFilePath
                    GogenieQuickCommandSpec.ScopeTarget.DIR -> sourceDir
                }

                val (executable, prefixArgs) = resolveCommand(project)
                val commandLine = GeneralCommandLine()
                    .withExePath(executable)
                    .withWorkDirectory(moduleDir.toFile())
                    .withCharset(StandardCharsets.UTF_8)
                    .withEnvironment("LC_ALL", "C.UTF-8")

                commandLine.addParameters(prefixArgs)
                commandLine.addParameters(command.args)
                if (!command.scopeFlag.isNullOrBlank() && !scope.isNullOrBlank()) {
                    commandLine.addParameters(command.scopeFlag, scope)
                }

                val result = runCatching {
                    CapturingProcessHandler(commandLine).runProcess(10 * 60 * 1000)
                }.getOrElse { e ->
                    notify(
                        project,
                        NotificationType.ERROR,
                        "执行 gogenie ${command.commandLabel} 失败",
                        e.message ?: e.javaClass.simpleName,
                    )
                    return
                }

                if (result.exitCode == 0) {
                    VirtualFileManager.getInstance().asyncRefresh(null)
                    val stdout = result.stdout.trim().ifBlank { "已执行 gogenie ${command.commandLabel}" }
                    notify(
                        project,
                        NotificationType.INFORMATION,
                        "gogenie ${command.commandLabel} 执行成功",
                        "注解 @$annotationName\n$stdout",
                    )
                } else {
                    val stderr = result.stderr.trim().ifBlank { result.stdout.trim() }.ifBlank { "未知错误" }
                    notify(
                        project,
                        NotificationType.ERROR,
                        "gogenie ${command.commandLabel} 执行失败（exit=${result.exitCode}）",
                        stderr,
                    )
                }
            }
        }.queue()
    }

    fun generateEnum(
        project: Project,
        sourceFilePath: String,
        enumName: String,
    ) {
        val moduleDir = findGoModuleDir(sourceFilePath)
        if (moduleDir == null) {
            notify(
                project,
                NotificationType.ERROR,
                "未找到 go.mod，无法执行 gogenie enum",
                "请在 Go 模块项目中使用该功能",
            )
            return
        }

        object : Task.Backgroundable(project, "gogenie: 生成枚举 $enumName", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "执行 gogenie enum --name $enumName"
                saveAllDocumentsOnEdt()

                val (executable, prefixArgs) = resolveCommand(project)
                val withName = runQuickProcess(
                    executable = executable,
                    prefixArgs = prefixArgs,
                    workDir = moduleDir,
                    args = listOf("enum", "--name", enumName, "--scope", sourceFilePath),
                )
                if (withName == null) {
                    notify(project, NotificationType.ERROR, "执行 gogenie enum 失败", "命令启动失败，请检查 gogenie 是否可执行")
                    return
                }

                val finalResult = if (withName.exitCode != 0 && isNameFlagUnsupported(withName)) {
                    runQuickProcess(
                        executable = executable,
                        prefixArgs = prefixArgs,
                        workDir = moduleDir,
                        args = listOf("enum", "--scope", sourceFilePath),
                    ) ?: withName
                } else {
                    withName
                }

                if (finalResult.exitCode == 0) {
                    VirtualFileManager.getInstance().asyncRefresh(null)
                    val stdout = finalResult.stdout.trim().ifBlank { "已生成枚举 $enumName" }
                    notify(project, NotificationType.INFORMATION, "gogenie enum 执行成功", stdout)
                } else {
                    val stderr = finalResult.stderr.trim().ifBlank { finalResult.stdout.trim() }.ifBlank { "未知错误" }
                    notify(
                        project,
                        NotificationType.ERROR,
                        "gogenie enum 执行失败（exit=${finalResult.exitCode}）",
                        stderr,
                    )
                }
            }
        }.queue()
    }

    private fun resolveCommand(project: Project): Pair<String, List<String>> {
        val fromEnv = System.getenv("GOGENIE_BIN")?.trim().orEmpty()
        if (fromEnv.isNotEmpty()) {
            return fromEnv to emptyList()
        }

        val basePath = project.basePath?.let(Paths::get)
        if (basePath != null) {
            val localMain = basePath.resolve("gogenie/main.go")
            val localMod = basePath.resolve("gogenie/go.mod")
            if (Files.exists(localMain) && Files.exists(localMod)) {
                return "go" to listOf("run", basePath.resolve("gogenie").toAbsolutePath().toString())
            }
        }

        val inPath = PathEnvironmentVariableUtil.findInPath("gogenie")
        if (inPath != null) {
            return inPath.absolutePath to emptyList()
        }

        return "gogenie" to emptyList()
    }

    private fun runImpl(
        executable: String,
        prefixArgs: List<String>,
        workDir: Path,
        annotationName: String,
        interfaceName: String,
        scope: String,
        withName: Boolean,
    ): ProcessOutput? {
        val commandLine = GeneralCommandLine()
            .withExePath(executable)
            .withWorkDirectory(workDir.toFile())
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment("LC_ALL", "C.UTF-8")

        commandLine.addParameters(prefixArgs)
        val args = mutableListOf("impl", annotationName, "--scope", scope)
        if (withName) {
            args += listOf("--name", interfaceName)
        }
        commandLine.addParameters(args)

        return runCatching {
            CapturingProcessHandler(commandLine).runProcess(10 * 60 * 1000)
        }.getOrNull()
    }

    private fun runQuickProcess(
        executable: String,
        prefixArgs: List<String>,
        workDir: Path,
        args: List<String>,
    ): ProcessOutput? {
        val commandLine = GeneralCommandLine()
            .withExePath(executable)
            .withWorkDirectory(workDir.toFile())
            .withCharset(StandardCharsets.UTF_8)
            .withEnvironment("LC_ALL", "C.UTF-8")
        commandLine.addParameters(prefixArgs)
        commandLine.addParameters(args)
        return runCatching {
            CapturingProcessHandler(commandLine).runProcess(10 * 60 * 1000)
        }.getOrNull()
    }

    private fun isNameFlagUnsupported(output: ProcessOutput): Boolean {
        val msg = (output.stderr + "\n" + output.stdout).lowercase()
        return msg.contains("unknown flag: --name") ||
            msg.contains("flag provided but not defined")
    }

    private fun isNoAnnotationError(output: ProcessOutput): Boolean {
        val msg = output.stderr + "\n" + output.stdout
        return msg.contains("未找到带有 @") && msg.contains("注解的接口定义")
    }

    private fun findGoModuleDir(sourceFilePath: String): Path? {
        var current: Path? = runCatching { Paths.get(sourceFilePath).toAbsolutePath().parent }.getOrNull()
        while (current != null) {
            if (Files.exists(current.resolve("go.mod"))) {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun notify(project: Project, type: NotificationType, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("gogenie.impl")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun saveAllDocumentsOnEdt() {
        val app = ApplicationManager.getApplication()
        val saveAction = Runnable {
            WriteIntentReadAction.run<RuntimeException> {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
        }
        if (app.isDispatchThread) {
            saveAction.run()
            return
        }
        app.invokeAndWait(saveAction)
    }
}
