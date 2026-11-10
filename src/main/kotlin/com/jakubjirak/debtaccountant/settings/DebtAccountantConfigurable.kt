package com.jakubjirak.debtaccountant.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.*

class DebtAccountantConfigurable : BoundConfigurable("Technical Debt Accountant") {

    private val settings get() = DebtAccountantSettings.getInstance()

    override fun createPanel() = panel {
        group("Valuation") {
            row("Hourly rate:") {
                textField()
                    .bindText(
                        { settings.hourlyRate.toString() },
                        { settings.hourlyRate = it.toDoubleOrNull() ?: 80.0 }
                    )
                    .comment("Your hourly rate for calculating debt cost")
            }
            row("Currency:") {
                comboBox(listOf("USD", "EUR", "GBP", "CZK"))
                    .bindItem(
                        { settings.currency },
                        { settings.currency = it ?: "USD" }
                    )
            }
            row("Monthly debt budget:") {
                textField()
                    .bindText(
                        { settings.debtBudgetDollars.toString() },
                        { settings.debtBudgetDollars = it.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 }
                    )
                    .comment("Status bar flags the project as over budget when monthly interest exceeds this (0 = no budget)")
            }
            row("") {
                comment("Used to calculate dollar value of tech debt")
            }
        }
        group("Storage") {
            row("Inventory path:") {
                cell(TextFieldWithBrowseButton().apply {
                    addBrowseFolderListener(
                        null,
                        FileChooserDescriptorFactory.createSingleFolderDescriptor()
                            .withTitle("Select Inventory Folder")
                    )
                })
                    .bindText(
                        { settings.inventoryPath },
                        { settings.inventoryPath = it }
                    )
                    .comment("Directory where tech debt inventory is stored")
            }
            row {
                checkBox("Auto-detect debt items")
                    .bindSelected(
                        { settings.autoDetectEnabled },
                        { settings.autoDetectEnabled = it }
                    )
                    .comment("Automatically scan for tech debt markers in the project")
            }
        }
        group("Display") {
            row {
                checkBox("Show gutter icons")
                    .bindSelected(
                        { settings.showGutterIcons },
                        { settings.showGutterIcons = it }
                    )
                    .comment("Display icons in the editor gutter for debt items")
            }
        }
    }
}
