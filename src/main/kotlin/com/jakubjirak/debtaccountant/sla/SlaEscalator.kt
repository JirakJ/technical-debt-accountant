package com.jakubjirak.debtaccountant.sla

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jakubjirak.debtaccountant.store.DebtInventoryStore

/**
 * Applies [EscalationAction]s to a project's [DebtInventoryStore].
 *
 * Each escalation is applied via a safe iterative update — the store's
 * internal save() is invoked per-update so a partial failure cannot lose
 * previously-applied escalations. Every successful escalation is announced
 * via the `Tech Debt Accountant` notification group.
 */
class SlaEscalator(private val project: Project) {

    /**
     * Apply all [escalations] to [store]. Returns the number of items actually updated
     * (items that were not found or whose priority already matched the target are skipped).
     */
    fun apply(escalations: List<EscalationAction>, store: DebtInventoryStore): Int {
        var applied = 0
        for (action in escalations) {
            val current = store.getItemById(action.item.id)
            if (current == null || current.priority == action.to) continue
            val updated = current.copy(
                priority = action.to,
                updatedAt = System.currentTimeMillis(),
            )
            try {
                store.updateItem(updated)
                applied++
                notify("SLA escalation: ${current.id} ${action.from.displayName} → ${action.to.displayName}")
            } catch (e: Exception) {
                LOG.warn("Failed to escalate item ${current.id}: ${e.message}", e)
            }
        }
        return applied
    }

    private fun notify(message: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Tech Debt Accountant")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            LOG.info("Notification suppressed: ${e.message}")
        }
    }

    companion object {
        private val LOG = Logger.getInstance(SlaEscalator::class.java)
    }
}
