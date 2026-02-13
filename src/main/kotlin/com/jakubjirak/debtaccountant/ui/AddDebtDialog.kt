package com.jakubjirak.debtaccountant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.jakubjirak.debtaccountant.licensing.FreemiumGate
import com.jakubjirak.debtaccountant.model.DebtCategory
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField

class AddDebtDialog(
    private val project: Project,
    private val store: DebtInventoryStore,
    private val prefilledFile: String = "",
    private val prefilledLine: Int = 0
) : DialogWrapper(project) {

    private val fileField = JTextField(prefilledFile, 30)
    private val lineStartField = JTextField(prefilledLine.toString(), 5)
    private val lineEndField = JTextField(prefilledLine.toString(), 5)
    private val categoryCombo = JComboBox(DebtCategory.entries.toTypedArray())
    private val priorityCombo = JComboBox(DebtPriority.entries.toTypedArray())
    private val descriptionArea = JTextArea(3, 30)
    private val fixTimeField = JTextField("60", 5)
    private val tagsField = JTextField(20)

    init {
        title = "Add Technical Debt Item"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        panel.add(createRow("File:", fileField))
        panel.add(createRow("Line Start:", lineStartField))
        panel.add(createRow("Line End:", lineEndField))
        panel.add(createRow("Category:", categoryCombo))
        panel.add(createRow("Priority:", priorityCombo))

        val descPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        descPanel.add(JLabel("Description:"))
        descPanel.add(JScrollPane(descriptionArea))
        panel.add(descPanel)

        panel.add(createRow("Est. Fix (min):", fixTimeField))
        panel.add(createRow("Tags (comma-sep):", tagsField))

        return panel
    }

    private fun createRow(label: String, component: JComponent): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT))
        row.add(JLabel(label))
        row.add(component)
        return row
    }

    override fun doOKAction() {
        val gate = FreemiumGate.getInstance()
        if (!gate.canAddItem(store.getItemCount())) {
            JOptionPane.showMessageDialog(
                contentPanel,
                "Free tier limit reached (${FreemiumGate.MAX_FREE_ITEMS} items). Upgrade to Pro for unlimited.",
                "Free Tier Limit",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val item = TechDebtItem(
            filePath = fileField.text,
            lineStart = lineStartField.text.toIntOrNull() ?: 0,
            lineEnd = lineEndField.text.toIntOrNull() ?: 0,
            category = categoryCombo.selectedItem as DebtCategory,
            priority = priorityCombo.selectedItem as DebtPriority,
            description = descriptionArea.text,
            estimatedFixMinutes = fixTimeField.text.toIntOrNull() ?: 60,
            tags = tagsField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        )

        store.addItem(item)
        super.doOKAction()
    }
}
