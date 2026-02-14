package com.jakubjirak.debtaccountant.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import com.jakubjirak.debtaccountant.tracker.WorkaroundTracker
import javax.swing.JOptionPane
import javax.swing.JTextField

class LogWorkaroundAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val store = DebtInventoryStore.getInstance(project)
        val items = store.getActiveItems()

        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(
                null, "No active debt items to log time against.", "No Items", JOptionPane.INFORMATION_MESSAGE
            )
            return
        }

        val itemNames = items.map { "${it.id.take(8)}: ${it.description.take(40)}" }.toTypedArray()
        val selected = JOptionPane.showInputDialog(
            null,
            "Select debt item:",
            "Log Workaround Time",
            JOptionPane.QUESTION_MESSAGE,
            null,
            itemNames,
            itemNames.firstOrNull()
        ) as? String ?: return

        val itemId = items[itemNames.indexOf(selected)].id
        val minutesField = JTextField("15")
        val noteField = JTextField()
        val fields = arrayOf<Any>(
            "Minutes spent:", minutesField,
            "Note (optional):", noteField
        )
        val result = JOptionPane.showConfirmDialog(null, fields, "Log Workaround", JOptionPane.OK_CANCEL_OPTION)
        if (result == JOptionPane.OK_OPTION) {
            val minutes = minutesField.text.toIntOrNull() ?: 15
            val note = noteField.text
            WorkaroundTracker.getInstance(project).logManual(itemId, minutes, note)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
