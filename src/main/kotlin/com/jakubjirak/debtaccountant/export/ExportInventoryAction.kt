package com.jakubjirak.debtaccountant.export

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jakubjirak.debtaccountant.settings.DebtAccountantSettings
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import javax.swing.ButtonGroup
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Tools → "Export Debt Inventory" action.
 *
 * Exports the inventory as CSV or JSON (with computed dollar columns) via a
 * save dialog, so the backlog can be shared with management in Excel/Sheets
 * or piped into other tooling.
 */
class ExportInventoryAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = DebtInventoryStore.getInstance(project)

        val options = ExportOptionsDialog(project).also { it.show() }
        if (!options.isOK) return

        val items = if (options.includeResolved()) store.getItems() else store.getActiveItems()
        if (items.isEmpty()) {
            Messages.showInfoMessage(project, "The debt inventory is empty — nothing to export.", "Export Debt Inventory")
            return
        }

        val settings = DebtAccountantSettings.getInstance()
        val csv = options.isCsv()
        val extension = if (csv) "csv" else "json"
        val body = if (csv) {
            InventoryExporter.toCsv(items, settings.hourlyRate, settings.currency)
        } else {
            InventoryExporter.toJson(items, settings.hourlyRate, settings.currency)
        }

        val descriptor = FileSaverDescriptor("Export Debt Inventory", "Save the debt inventory", extension)
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save(null as com.intellij.openapi.vfs.VirtualFile?, "debt-inventory.$extension") ?: return
        val path = wrapper.file.toPath()
        try {
            Files.writeString(path, body, StandardCharsets.UTF_8)
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "Failed to write file: ${ex.message}", "Export Debt Inventory")
            return
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tech Debt Accountant")
            .createNotification(
                "Exported ${items.size} debt item(s) to ${path.toAbsolutePath()}.",
                NotificationType.INFORMATION,
            ).notify(project)
    }
}

/** Format and scope options for the export. */
internal class ExportOptionsDialog(project: Project) : DialogWrapper(project, true) {
    private val csvRadio = JRadioButton("CSV — opens in Excel / Google Sheets", true)
    private val jsonRadio = JRadioButton("JSON — summary plus per-item costs")
    private val resolvedCheck = JBCheckBox("Include resolved items")

    init {
        title = "Export Debt Inventory"
        ButtonGroup().apply {
            add(csvRadio)
            add(jsonRadio)
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = JBUI.Borders.empty(8)
        panel.add(JBLabel("Exports include workaround and fix costs computed from your hourly rate."))
        panel.add(csvRadio)
        panel.add(jsonRadio)
        panel.add(resolvedCheck)
        return panel
    }

    fun isCsv(): Boolean = csvRadio.isSelected

    fun includeResolved(): Boolean = resolvedCheck.isSelected
}
