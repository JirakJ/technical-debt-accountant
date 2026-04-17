package com.jakubjirak.debtaccountant.sla

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Tools → "Edit Debt SLA Policy" action. Opens `.debt-sla.json` in the editor,
 * creating it from [SlaPolicy.DEFAULT] if the file does not yet exist.
 */
class EditSlaPolicyAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val root = project.basePath?.let { Paths.get(it) } ?: return
        SlaPolicyEditor.open(project, root)
    }
}

/** Helper used by both [EditSlaPolicyAction] and the evaluation dialog. */
internal object SlaPolicyEditor {

    /** Opens `<projectRoot>/.debt-sla.json` in the editor, creating defaults if absent. */
    fun open(project: Project, projectRoot: Path) {
        val file = projectRoot.resolve(SlaPolicyStore.FILE_NAME)
        if (!Files.exists(file)) {
            SlaPolicyStore.save(SlaPolicy.DEFAULT, projectRoot)
        }
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
            ?: VfsUtil.findFileByIoFile(file.toFile(), true)
            ?: return
        FileEditorManager.getInstance(project).openTextEditor(
            OpenFileDescriptor(project, vf), true
        )
    }
}
