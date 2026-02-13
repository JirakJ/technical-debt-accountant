package com.jakubjirak.debtaccountant.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.jakubjirak.debtaccountant.model.DebtPriority
import com.jakubjirak.debtaccountant.model.TechDebtItem
import javax.swing.Icon

class DebtGutterIconRenderer(private val item: TechDebtItem) : GutterIconRenderer() {

    override fun getIcon(): Icon = when (item.priority) {
        DebtPriority.CRITICAL -> AllIcons.General.Error
        DebtPriority.HIGH -> AllIcons.General.Warning
        DebtPriority.MEDIUM -> AllIcons.General.Information
        DebtPriority.LOW -> AllIcons.General.Note
    }

    override fun getTooltipText(): String {
        return "[${item.category.displayName}] ${item.description} (${item.totalWorkaroundMinutes}min workaround)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DebtGutterIconRenderer) return false
        return item.id == other.item.id
    }

    override fun hashCode(): Int = item.id.hashCode()
}
