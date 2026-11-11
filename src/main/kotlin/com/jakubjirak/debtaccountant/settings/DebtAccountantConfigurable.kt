package com.jakubjirak.debtaccountant.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class DebtAccountantConfigurable : BoundConfigurable("Technical Debt Accountant") {

    private val settings get() = DebtAccountantSettings.getInstance()

    override fun createPanel() = panel {
        group("Valuation") {
            row("Hourly rate:") {
                textField()
                    .bindText(
                        { settings.hourlyRate.toString() },
                        { settings.hourlyRate = SettingsInput.parseHourlyRate(it) }
                    )
                    .comment("Your hourly rate for calculating debt cost")
            }
            row("Currency:") {
                comboBox(SettingsInput.SUPPORTED_CURRENCIES)
                    .bindItem(
                        { settings.currency },
                        { settings.currency = it ?: SettingsInput.DEFAULT_CURRENCY }
                    )
            }
            row("Monthly debt budget:") {
                textField()
                    .bindText(
                        { settings.debtBudgetDollars.toString() },
                        { settings.debtBudgetDollars = SettingsInput.parseBudget(it) }
                    )
                    .comment("Status bar flags the project as over budget when monthly interest exceeds this (0 = no budget)")
            }
            row("") {
                comment("Used to calculate dollar value of tech debt")
            }
        }
    }
}
