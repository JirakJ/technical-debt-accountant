package com.jakubjirak.debtaccountant.sla

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.DefaultTableModel

/**
 * Tools → "Evaluate Debt SLAs" action.
 *
 * Loads the project SLA policy, evaluates the active inventory in a background task,
 * and shows the results in a three-tab dialog (Breached / Approaching / Escalations)
 * with Copy Markdown / Export .md / Open Policy Config / Close controls. Optionally
 * applies escalations via [SlaEscalator].
 */
class EvaluateSlaAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val intro = SlaIntroDialog(project).also { it.show() }
        if (!intro.isOK) return
        val applyEscalations = intro.shouldApplyEscalations()

        val store = DebtInventoryStore.getInstance(project)
        val items = store.getItems()
        val projectRoot = project.basePath?.let { Paths.get(it) } ?: Paths.get(".")
        val policy = SlaPolicyStore.load(projectRoot)
        val policyErrors = policy.validate()

        if (policyErrors.isNotEmpty()) {
            Messages.showErrorDialog(
                project,
                "Policy file is invalid:\n" + policyErrors.joinToString("\n") { "• $it" },
                "Debt SLA Policy",
            )
            return
        }

        object : Task.Backgroundable(project, "Evaluating debt SLAs…", false) {
            private var result: SlaEvaluation? = null
            private var applied: Int = 0

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val eval = SlaEvaluator.evaluate(items, policy, Instant.now())
                result = eval
                if (applyEscalations && eval.escalations.isNotEmpty()) {
                    applied = SlaEscalator(project).apply(eval.escalations, store)
                }
            }

            override fun onSuccess() {
                val eval = result ?: return
                ApplicationManager.getApplication().invokeLater {
                    SlaResultDialog(project, eval, projectRoot).show()
                    if (applyEscalations) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Tech Debt Accountant")
                            .createNotification(
                                "Applied $applied SLA escalation(s).",
                                NotificationType.INFORMATION,
                            ).notify(project)
                    }
                }
            }
        }.queue()
    }
}

/** Pre-run dialog asking whether to apply escalations. */
internal class SlaIntroDialog(project: Project) : DialogWrapper(project, true) {
    private val applyCheck = JBCheckBox("Apply escalations now (updates priorities of breached items)")

    init {
        title = "Evaluate Debt SLAs"
        applyCheck.isSelected = false
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8)
        panel.add(JBLabel("Evaluates every active debt item against the project SLA policy."))
        panel.add(JBLabel("Policy file: .debt-sla.json (edit via Tools → Edit Debt SLA Policy)."))
        panel.add(JBUI.Borders.emptyTop(6).let { JPanel() })
        panel.add(applyCheck)
        return panel
    }

    fun shouldApplyEscalations(): Boolean = applyCheck.isSelected
}

/** Results dialog with three tabs + Copy / Export / Open policy / Close buttons. */
internal class SlaResultDialog(
    private val project: Project,
    private val eval: SlaEvaluation,
    private val projectRoot: java.nio.file.Path,
) : DialogWrapper(project, true) {

    private val markdown: String = SlaMarkdownReporter.render(eval, project.name)

    init {
        title = "Debt SLA Evaluation — ${eval.summary.breachedCount} breached, ${eval.summary.approachingCount} approaching"
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<javax.swing.Action> = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(900, 560)
        root.border = JBUI.Borders.empty(8)

        val summary = JBLabel(
            "Evaluated: ${eval.summary.totalEvaluated} · OK: ${eval.summary.okCount} · " +
                "Approaching: ${eval.summary.approachingCount} · Breached: ${eval.summary.breachedCount} · " +
                "Escalations: ${eval.summary.escalationCount} · Worst breach: ${eval.summary.worstBreachDays}d"
        )
        summary.border = JBUI.Borders.emptyBottom(6)
        root.add(summary, BorderLayout.NORTH)

        val tabs = JBTabbedPane()
        tabs.addTab("Breached (${eval.breached.size})", breachedTab())
        tabs.addTab("Approaching (${eval.approaching.size})", approachingTab())
        tabs.addTab("Escalations (${eval.escalations.size})", escalationsTab())
        root.add(tabs, BorderLayout.CENTER)

        val buttons = JPanel()
        buttons.layout = BoxLayout(buttons, BoxLayout.X_AXIS)
        buttons.add(JButton("Copy Markdown").apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(markdown), null)
                Messages.showInfoMessage(project, "Copied ${markdown.length} chars.", "Copy Markdown")
            }
        })
        buttons.add(JButton("Export .md…").apply {
            addActionListener { exportMarkdown() }
        })
        buttons.add(JButton("Open SLA Policy Config").apply {
            addActionListener {
                SlaPolicyEditor.open(project, projectRoot)
            }
        })
        root.add(buttons, BorderLayout.SOUTH)
        return root
    }

    private fun breachedTab(): JComponent {
        val columns = arrayOf("ID", "Description", "Priority", "Age (d)", "Over by", "Path")
        val model = DefaultTableModel(columns, 0)
        eval.breached.forEach { v ->
            model.addRow(arrayOf(v.item.id, v.item.description, v.item.priority.displayName, v.ageDays, v.daysOver, v.item.filePath))
        }
        return JBScrollPane(JBTable(model))
    }

    private fun approachingTab(): JComponent {
        val columns = arrayOf("ID", "Description", "Priority", "Age (d)", "Days to breach", "Path")
        val model = DefaultTableModel(columns, 0)
        eval.approaching.forEach { w ->
            model.addRow(arrayOf(w.item.id, w.item.description, w.item.priority.displayName, w.ageDays, w.daysToBreach, w.item.filePath))
        }
        return JBScrollPane(JBTable(model))
    }

    private fun escalationsTab(): JComponent {
        val columns = arrayOf("ID", "From", "To", "Description")
        val model = DefaultTableModel(columns, 0)
        eval.escalations.forEach { esc ->
            model.addRow(arrayOf(esc.item.id, esc.from.displayName, esc.to.displayName, esc.item.description))
        }
        return JBScrollPane(JBTable(model))
    }

    private fun exportMarkdown() {
        val descriptor = FileSaverDescriptor("Export SLA Report", "Save the SLA report as Markdown", "md")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "sla-report.md") ?: return
        val path = wrapper.file.toPath()
        Files.writeString(path, markdown, StandardCharsets.UTF_8)
        Messages.showInfoMessage(project, "Saved to ${path.toAbsolutePath()}.", "Export SLA Report")
    }
}
