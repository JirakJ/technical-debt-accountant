package com.jakubjirak.debtaccountant.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/** Registers the technical-debt health widget in the IDE status bar. */
class DebtHealthStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = DebtHealthStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Technical Debt Health"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = DebtHealthStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
