package com.jakubjirak.debtaccountant.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.jakubjirak.debtaccountant.licensing.FreemiumGate
import com.jakubjirak.debtaccountant.report.ReportGenerator
import com.jakubjirak.debtaccountant.settings.DebtAccountantSettings
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JOptionPane

class GenerateReportAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val gate = FreemiumGate.getInstance()

        if (!gate.isPaidFeatureAvailable(FreemiumGate.FEATURE_EXPORT)) {
            JOptionPane.showMessageDialog(
                null, "Report export is a Pro feature.", "Pro Feature", JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val store = DebtInventoryStore.getInstance(project)
        val settings = DebtAccountantSettings.getInstance()
        val generator = ReportGenerator()
        val report = generator.generateReport(project.name, store.getItems(), settings.currency)
        val markdown = generator.toMarkdown(report)

        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(markdown), null)
        JOptionPane.showMessageDialog(
            null, "Report copied to clipboard!", "Report Generated", JOptionPane.INFORMATION_MESSAGE
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
