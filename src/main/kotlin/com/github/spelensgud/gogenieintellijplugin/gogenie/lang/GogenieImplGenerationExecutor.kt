package com.github.spelensgud.gogenieintellijplugin.gogenie.lang

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
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
        if (app.isDispatchThread) {
            FileDocumentManager.getInstance().saveAllDocuments()
            return
        }
        app.invokeAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
        }
    }
}
