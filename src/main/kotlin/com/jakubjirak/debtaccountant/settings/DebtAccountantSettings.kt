package com.jakubjirak.debtaccountant.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "DebtAccountantSettings",
    storages = [Storage("debtAccountantSettings.xml")]
)
class DebtAccountantSettings : PersistentStateComponent<DebtAccountantSettings.State> {

    data class State(
        var hourlyRate: Double = 80.0,
        var currency: String = "USD",
        var inventoryPath: String = ".tech-debt",
        var autoDetectEnabled: Boolean = true,
        var maxFreeItems: Int = 50,
        var showGutterIcons: Boolean = true,
        var debtBudgetDollars: Double = 0.0
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var hourlyRate: Double
        get() = state.hourlyRate
        set(value) { state.hourlyRate = value }

    var currency: String
        get() = state.currency
        set(value) { state.currency = value }

    var inventoryPath: String
        get() = state.inventoryPath
        set(value) { state.inventoryPath = value }

    var autoDetectEnabled: Boolean
        get() = state.autoDetectEnabled
        set(value) { state.autoDetectEnabled = value }

    var maxFreeItems: Int
        get() = state.maxFreeItems
        set(value) { state.maxFreeItems = value }

    var showGutterIcons: Boolean
        get() = state.showGutterIcons
        set(value) { state.showGutterIcons = value }

    /** Monthly debt-interest budget in [currency]; `0.0` disables budget checks. */
    var debtBudgetDollars: Double
        get() = state.debtBudgetDollars
        set(value) { state.debtBudgetDollars = value }

    companion object {
        fun getInstance(): DebtAccountantSettings =
            ApplicationManager.getApplication().getService(DebtAccountantSettings::class.java)
    }
}
