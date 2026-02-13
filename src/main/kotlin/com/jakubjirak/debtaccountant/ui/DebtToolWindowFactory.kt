package com.jakubjirak.debtaccountant.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.jakubjirak.debtaccountant.licensing.FreemiumGate
import com.jakubjirak.debtaccountant.report.ReportGenerator
import com.jakubjirak.debtaccountant.settings.DebtAccountantSettings
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

class DebtToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val store = DebtInventoryStore.getInstance(project)
        store.load()

        val mainPanel = JPanel(BorderLayout())

        val summaryLabel = JLabel()
        updateSummaryLabel(store, summaryLabel)
        val summaryPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        summaryPanel.add(summaryLabel)
        mainPanel.add(summaryPanel, BorderLayout.NORTH)

        val columns = arrayOf("ID", "File", "Category", "Priority", "Description", "Workaround (min)", "Resolved")
        val tableModel = ReadOnlyTableModel(columns, 0)
        populateTable(store, tableModel)
        val table = JTable(tableModel)
        table.autoCreateRowSorter = true
        mainPanel.add(JBScrollPane(table), BorderLayout.CENTER)

        val actionsPanel = createActionsPanel(project, store, tableModel, table)
        mainPanel.add(actionsPanel, BorderLayout.SOUTH)

        store.addListener {
            populateTable(store, tableModel)
            updateSummaryLabel(store, summaryLabel)
        }

        val content = ContentFactory.getInstance().createContent(mainPanel, "Debt Inventory", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun updateSummaryLabel(store: DebtInventoryStore, label: JLabel) {
        val activeCount = store.getActiveItemCount()
        val gate = FreemiumGate.getInstance()
        val valuator = DollarValuator()

        if (gate.isPaidFeatureAvailable(FreemiumGate.FEATURE_DOLLAR_VALUATION)) {
            val totalCost = valuator.totalWorkaroundCost(store.getActiveItems())
            val settings = DebtAccountantSettings.getInstance()
            label.text = "Active Items: $activeCount  |  " +
                "Total Workaround Cost: ${valuator.formatCurrency(totalCost, settings.currency)}"
        } else {
            label.text = "Active Items: $activeCount  |  Total Workaround Cost: [Pro feature]"
        }
    }

    private fun populateTable(store: DebtInventoryStore, tableModel: DefaultTableModel) {
        tableModel.setRowCount(0)
        store.getItems().forEach { item ->
            tableModel.addRow(
                arrayOf<Any>(
                    item.id.take(8),
                    item.filePath,
                    item.category.displayName,
                    item.priority.displayName,
                    item.description,
                    item.totalWorkaroundMinutes,
                    item.isResolved
                )
            )
        }
    }

    private fun createActionsPanel(
        project: Project,
        store: DebtInventoryStore,
        tableModel: DefaultTableModel,
        table: JTable
    ): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))

        val addButton = JButton("Add Debt Item")
        addButton.addActionListener {
            AddDebtDialog(project, store).show()
        }

        val resolveButton = JButton("Resolve Selected")
        resolveButton.addActionListener {
            val selectedRow = table.selectedRow
            if (selectedRow >= 0) {
                val modelRow = table.convertRowIndexToModel(selectedRow)
                val idPrefix = tableModel.getValueAt(modelRow, 0) as String
                val item = store.getItems().find { it.id.startsWith(idPrefix) }
                if (item != null) store.resolveItem(item.id)
            }
        }

        val reportButton = JButton("Generate Report")
        reportButton.addActionListener {
            val gate = FreemiumGate.getInstance()
            if (gate.isPaidFeatureAvailable(FreemiumGate.FEATURE_EXPORT)) {
                val settings = DebtAccountantSettings.getInstance()
                val generator = ReportGenerator()
                val report = generator.generateReport(project.name, store.getItems(), settings.currency)
                val markdown = generator.toMarkdown(report)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(markdown), null)
                JOptionPane.showMessageDialog(
                    null, "Report copied to clipboard!", "Report", JOptionPane.INFORMATION_MESSAGE
                )
            } else {
                JOptionPane.showMessageDialog(
                    null, "Report export is a Pro feature.", "Pro Feature", JOptionPane.INFORMATION_MESSAGE
                )
            }
        }

        panel.add(addButton)
        panel.add(resolveButton)
        panel.add(reportButton)
        return panel
    }
}

class ReadOnlyTableModel(columns: Array<String>, rowCount: Int) : DefaultTableModel(columns, rowCount) {
    override fun isCellEditable(row: Int, column: Int): Boolean = false
}
