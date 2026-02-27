package com.github.spelensgud.gogenieintellijplugin.gogenie.action

import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieImplGenerationExecutor
import com.github.spelensgud.gogenieintellijplugin.gogenie.lang.GogenieQuickCommandSpec
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class GogenieDb2StructAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isGoFile = psiFile?.language?.id.equals("go", ignoreCase = true) ||
            vFile?.extension.equals("go", ignoreCase = true)

        e.presentation.isVisible = project != null && isGoFile
        e.presentation.isEnabled = project != null && isGoFile && vFile != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        GogenieImplGenerationExecutor.runQuickCommand(
            project = project,
            sourceFilePath = vFile.path,
            annotationName = null,
            command = GogenieQuickCommandSpec(
                commandLabel = "db2struct",
                args = listOf("db2struct"),
            ),
        )
    }
}
