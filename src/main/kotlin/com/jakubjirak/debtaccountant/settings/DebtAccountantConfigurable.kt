package com.jakubjirak.debtaccountant.settings

import com.intellij.openapi.options.Configurable
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

class DebtAccountantConfigurable : Configurable {

    private var panel: JPanel? = null
    private var hourlyRateField: JTextField? = null
    private var currencyCombo: JComboBox<String>? = null
    private var inventoryPathField: JTextField? = null
    private var gutterIconsCheckbox: JCheckBox? = null

    override fun getDisplayName(): String = "Technical Debt Accountant"

    override fun createComponent(): JComponent {
        val settings = DebtAccountantSettings.getInstance()

        panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val ratePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Hourly Rate:"))
            hourlyRateField = JTextField(settings.hourlyRate.toString(), 10)
            add(hourlyRateField)
        }

        val currencyPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Currency:"))
            currencyCombo = JComboBox(arrayOf("USD", "EUR", "GBP", "CZK"))
            currencyCombo?.selectedItem = settings.currency
            add(currencyCombo)
        }

        val pathPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Inventory Path:"))
            inventoryPathField = JTextField(settings.inventoryPath, 20)
            add(inventoryPathField)
        }

        val gutterPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            gutterIconsCheckbox = JCheckBox("Show gutter icons for debt items", settings.showGutterIcons)
            add(gutterIconsCheckbox)
        }

        panel?.add(ratePanel)
        panel?.add(currencyPanel)
        panel?.add(pathPanel)
        panel?.add(gutterPanel)

        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = DebtAccountantSettings.getInstance()
        return hourlyRateField?.text?.toDoubleOrNull() != settings.hourlyRate ||
            currencyCombo?.selectedItem != settings.currency ||
            inventoryPathField?.text != settings.inventoryPath ||
            gutterIconsCheckbox?.isSelected != settings.showGutterIcons
    }

    override fun apply() {
        val settings = DebtAccountantSettings.getInstance()
        settings.hourlyRate = hourlyRateField?.text?.toDoubleOrNull() ?: 80.0
        settings.currency = currencyCombo?.selectedItem as? String ?: "USD"
        settings.inventoryPath = inventoryPathField?.text ?: ".tech-debt"
        settings.showGutterIcons = gutterIconsCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = DebtAccountantSettings.getInstance()
        hourlyRateField?.text = settings.hourlyRate.toString()
        currencyCombo?.selectedItem = settings.currency
        inventoryPathField?.text = settings.inventoryPath
        gutterIconsCheckbox?.isSelected = settings.showGutterIcons
    }
}
