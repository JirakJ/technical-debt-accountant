package com.jakubjirak.debtaccountant.statusbar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindowManager
import com.jakubjirak.debtaccountant.health.DebtHealthScorer
import com.jakubjirak.debtaccountant.settings.DebtAccountantSettings
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import com.intellij.util.Consumer
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import java.awt.Component
import java.awt.event.MouseEvent

/**
 * Status bar widget showing the project's technical-debt health grade and
 * monthly interest at a glance. Clicking it opens the Tech Debt tool window.
 * Refreshes automatically whenever the inventory changes.
 */
class DebtHealthStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    private val valuator = DollarValuator()
    private var statusBar: StatusBar? = null

    private val listener: () -> Unit = {
        // Inventory mutations can arrive on background threads; touch the status
        // bar only on the EDT.
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        DebtInventoryStore.getInstance(project).addListener(listener)
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val health = currentHealth()
        return StatusBarTextBuilder.widgetText(health, formatCurrency(health.monthlyInterest))
    }

    override fun getTooltipText(): String {
        val health = currentHealth()
        return StatusBarTextBuilder.tooltip(
            health,
            formatCurrency(health.monthlyInterest),
            formatCurrency(health.budgetDollars)
        )
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate(null)
    }

    override fun dispose() {
        statusBar = null
    }

    private fun currentHealth() = run {
        val store = DebtInventoryStore.getInstance(project)
        val active = store.getActiveItems()
        val settings = DebtAccountantSettings.getInstance()
        DebtHealthScorer.score(
            items = active,
            monthlyInterest = valuator.totalMonthlyInterest(active),
            budgetDollars = settings.debtBudgetDollars
        )
    }

    private fun formatCurrency(amount: Double): String =
        valuator.formatCurrency(amount, DebtAccountantSettings.getInstance().currency)

    companion object {
        const val WIDGET_ID = "TechDebtAccountant.HealthWidget"
        private const val TOOL_WINDOW_ID = "Tech Debt"
    }
}
