package com.jakubjirak.debtaccountant.scanner

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Tools → "Scan TODO/FIXME Comments" action.
 *
 * Walks the project tree in a background task with [DebtCommentScanner],
 * deduplicates against the existing inventory, previews the hits in a table
 * dialog, and imports the selected results as debt items.
 */
class ScanDebtCommentsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath
        if (basePath == null) {
            Messages.showErrorDialog(project, "Project has no base directory to scan.", "Scan Debt Comments")
            return
        }
        val store = DebtInventoryStore.getInstance(project)

        object : Task.Backgroundable(project, "Scanning for debt comments…", false) {
            private var found: List<ScannedComment> = emptyList()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val scanned = DebtCommentScanner.scanDirectory(Paths.get(basePath))
                found = DebtCommentScanner.dedupe(scanned, store.getItems())
            }

            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    if (found.isEmpty()) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Tech Debt Accountant")
                            .createNotification(
                                "No new TODO/FIXME/HACK comments found — inventory is up to date.",
                                NotificationType.INFORMATION,
                            ).notify(project)
                        return@invokeLater
                    }
                    val dialog = ScanResultDialog(project, found)
                    if (dialog.showAndGet()) {
                        found.forEach { store.addItem(DebtCommentScanner.toDebtItem(it)) }
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Tech Debt Accountant")
                            .createNotification(
                                "Imported ${found.size} debt item(s) from code comments.",
                                NotificationType.INFORMATION,
                            ).notify(project)
                    }
                }
            }
        }.queue()
    }
}

/** Preview dialog listing scan hits; OK imports them all. */
internal class ScanResultDialog(
    project: Project,
    private val comments: List<ScannedComment>,
) : DialogWrapper(project, true) {

    init {
        title = "Debt Comment Scan — ${comments.size} new"
        setOKButtonText("Import ${comments.size} Item(s)")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(820, 460)
        root.border = JBUI.Borders.empty(8)

        val header = JBLabel("New debt comments not yet in the inventory. OK imports them with marker-based category and priority.")
        header.border = JBUI.Borders.emptyBottom(6)
        root.add(header, BorderLayout.NORTH)

        val columns = arrayOf("Marker", "Priority", "Category", "Location", "Comment")
        val model = object : DefaultTableModel(columns, 0) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        comments.forEach { c ->
            model.addRow(
                arrayOf(
                    c.marker.name,
                    c.marker.priority.displayName,
                    c.marker.category.displayName,
                    "${c.filePath}:${c.line}",
                    c.text,
                )
            )
        }
        root.add(JBScrollPane(JBTable(model)), BorderLayout.CENTER)
        return root
    }
}
