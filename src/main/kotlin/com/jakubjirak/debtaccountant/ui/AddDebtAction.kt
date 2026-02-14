package com.jakubjirak.debtaccountant.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.jakubjirak.debtaccountant.store.DebtInventoryStore

class AddDebtAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = DebtInventoryStore.getInstance(project)

        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val filePath = file?.path?.let { path ->
            project.basePath?.let { base ->
                if (path.startsWith(base)) path.removePrefix(base).removePrefix("/") else path
            } ?: path
        } ?: ""
        val line = editor?.caretModel?.logicalPosition?.line?.plus(1) ?: 0

        AddDebtDialog(project, store, filePath, line).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
