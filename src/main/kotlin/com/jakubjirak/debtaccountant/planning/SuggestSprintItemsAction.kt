package com.jakubjirak.debtaccountant.planning

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel
import com.intellij.ui.JBIntSpinner

/**
 * Tools → "Suggest Sprint Items…" action.
 *
 * Collects a budget (hours), minimum priority, optional tag filters and an allow-overflow flag,
 * then runs [SprintPlanningService] in the background and presents the resulting [SprintPlan]
 * in a results dialog with Copy/Export/Close controls.
 */
class SuggestSprintItemsAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = SprintOptionsDialog(project).also { it.show() }
        if (!input.isOK) return

        val options = input.buildOptions()
        val store = DebtInventoryStore.getInstance(project)
        val items = store.getActiveItems()

        object : Task.Backgroundable(project, "Computing sprint plan…", false) {
            private var plan: SprintPlan? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                plan = SprintPlanningService().plan(items, options)
            }

            override fun onSuccess() {
                val result = plan ?: return
                ApplicationManager.getApplication().invokeLater {
                    SprintPlanResultDialog(project, result).show()
                }
            }
        }.queue()
    }
}

/**
 * Modal dialog collecting [SprintPlanningOptions] from the user. Uses JBUI components.
 */
internal class SprintOptionsDialog(private val project: Project) : DialogWrapper(project, true) {

    private val budgetSpinner = JBIntSpinner(40, 1, 10_000)
    private val priorityCombo = JComboBox(DebtPriority.values())
    private val includeTagsField = JBTextField()
    private val excludeTagsField = JBTextField()
    private val overflowCheck = JBCheckBox("Allow one overflow item (up to +20% of budget)")

    init {
        title = "Suggest Sprint Items"
        priorityCombo.selectedItem = DebtPriority.LOW
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 0.0
            gridx = 0
            gridy = 0
        }

        fun addRow(label: String, comp: JComponent) {
            gbc.gridx = 0; gbc.weightx = 0.0
            panel.add(JBLabel(label, SwingConstants.LEFT), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(comp, gbc)
            gbc.gridy++
        }

        addRow("Budget (hours):", budgetSpinner)
        addRow("Minimum priority:", priorityCombo)
        addRow("Include tags (comma-separated):", includeTagsField)
        addRow("Exclude tags (comma-separated):", excludeTagsField)

        gbc.gridx = 0
        gbc.gridwidth = 2
        panel.add(overflowCheck, gbc)

        panel.preferredSize = Dimension(480, panel.preferredSize.height)
        return panel
    }

    fun buildOptions(): SprintPlanningOptions {
        val valuator = DollarValuator()
        return SprintPlanningOptions(
            budgetHours = budgetSpinner.number.toDouble(),
            tagsIncluded = parseTags(includeTagsField.text),
            tagsExcluded = parseTags(excludeTagsField.text),
            minPriority = priorityCombo.selectedItem as DebtPriority,
            allowOverflowOnce = overflowCheck.isSelected,
            valuator = { item: TechDebtItem -> valuator.valuate(item).monthlyInterest }
        )
    }

    private fun parseTags(text: String): Set<String> =
        text.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}

/**
 * Result dialog presenting the produced [SprintPlan] and allowing copy/export.
 */
internal class SprintPlanResultDialog(
    private val project: Project,
    private val plan: SprintPlan
) : DialogWrapper(project, true) {

    init {
        title = "Sprint Plan — ${plan.itemCount} items, ${"%.2f".format(plan.totalHours)}h"
        setOKButtonText("Close")
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(820, 520)
        root.border = JBUI.Borders.empty(8)

        val summary = JPanel()
        summary.layout = BoxLayout(summary, BoxLayout.Y_AXIS)
        summary.add(JBLabel("Budget: ${"%.2f".format(plan.budgetHours)}h"))
        summary.add(JBLabel("Selected: ${"%.2f".format(plan.totalHours)}h over ${plan.itemCount} items"))
        summary.add(JBLabel("Monthly savings: \$${"%.2f".format(plan.totalMonthlyDollarSavings)}"))
        summary.add(JBLabel("Declined: ${plan.declined.size}"))
        root.add(summary, BorderLayout.NORTH)

        val columns = arrayOf<Any>("ID", "Title", "Priority", "Effort (h)", "Monthly $", "ROI", "Path")
        val model = object : DefaultTableModel(columns, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        for (rec in plan.selected) {
            model.addRow(
                arrayOf<Any>(
                    rec.item.id,
                    rec.item.description,
                    rec.item.priority.displayName,
                    "%.2f".format(rec.fixEffortHours),
                    "%.2f".format(rec.monthlyInterestSavedDollars),
                    "%.2f".format(rec.roi),
                    rec.item.filePath
                )
            )
        }
        val table = JBTable(model)
        root.add(JBScrollPane(table), BorderLayout.CENTER)

        val buttons = JPanel()
        val copyBtn = JButton("Copy Markdown").apply {
            addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(plan.toMarkdown()), null)
                Messages.showInfoMessage(project, "Markdown copied to clipboard.", "Sprint Plan")
            }
        }
        val exportBtn = JButton("Export .md").apply {
            addActionListener { exportMarkdown() }
        }
        buttons.add(copyBtn)
        buttons.add(exportBtn)
        root.add(buttons, BorderLayout.SOUTH)

        return root
    }

    private fun exportMarkdown() {
        val descriptor = FileSaverDescriptor("Export Sprint Plan", "Save the plan as a Markdown file.", "md")
        val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val target = saver.save(project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }, "sprint-plan.md")
        if (target != null) {
            val f = target.file
            f.parentFile?.mkdirs()
            f.writeText(plan.toMarkdown())
            Messages.showInfoMessage(project, "Saved to ${f.absolutePath}", "Sprint Plan")
        }
    }

    @Suppress("unused")
    private fun anchorImports() {
        // no-op
    }
}
