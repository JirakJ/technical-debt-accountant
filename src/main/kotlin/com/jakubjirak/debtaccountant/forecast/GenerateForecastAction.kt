package com.jakubjirak.debtaccountant.forecast

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.SpinnerNumberModel

/**
 * Tools menu action that runs the debt forecaster off-EDT on a user-selected
 * snapshots directory and presents a read-only preview with a Save As button.
 */
class GenerateForecastAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = ForecastOptionsDialog(project)
        if (!dialog.showAndGet()) return
        val snapshotsDir = dialog.snapshotsDir() ?: return
        val horizonWeeks = dialog.horizonWeeks()
        val model = dialog.model()
        val format = dialog.format()

        object : Task.Backgroundable(project, "Generating debt forecast…", true) {
            private var output: String = ""
            private var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val points = SnapshotHistoryLoader(snapshotsDir).load()
                    val forecast = DebtForecaster().forecast(points, horizonWeeks, model)
                    output = when (format) {
                        "html" -> ForecastHtmlReporter.render(forecast)
                        else -> ForecastMarkdownReporter.render(forecast)
                    }
                } catch (t: Throwable) {
                    error = t.message ?: t.javaClass.simpleName
                }
            }

            override fun onFinished() {
                ApplicationManager.getApplication().invokeLater {
                    val err = error
                    if (err != null) {
                        Messages.showErrorDialog(project, err, "Debt Forecast")
                    } else {
                        ForecastPreviewDialog(project, output, format).show()
                    }
                }
            }
        }.queue()
    }

    /** User-configurable options for the forecast. */
    private class ForecastOptionsDialog(private val project: Project) : DialogWrapper(project) {
        private val dirField = TextFieldWithBrowseButton().apply {
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Snapshots Directory")
                .withDescription("Select the directory containing digest snapshot JSON files")
            addBrowseFolderListener(project, descriptor)
        }
        private val horizonSpinner = JSpinner(SpinnerNumberModel(12, 1, 104, 1))
        private val modelCombo = JComboBox(arrayOf("auto", "linear", "exponential"))
        private val formatCombo = JComboBox(arrayOf("md", "html"))

        init {
            title = "Debt Trend Forecast"
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            panel.preferredSize = Dimension(480, 160)
            val gbc = GridBagConstraints().apply {
                insets = Insets(4, 4, 4, 4); anchor = GridBagConstraints.WEST
            }
            gbc.gridx = 0; gbc.gridy = 0; panel.add(JBLabel("Snapshots dir:"), gbc)
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
            panel.add(dirField, gbc)

            gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
            panel.add(JBLabel("Horizon (weeks):"), gbc)
            gbc.gridx = 1; panel.add(horizonSpinner, gbc)

            gbc.gridx = 0; gbc.gridy = 2; panel.add(JBLabel("Model:"), gbc)
            gbc.gridx = 1; panel.add(modelCombo, gbc)

            gbc.gridx = 0; gbc.gridy = 3; panel.add(JBLabel("Format:"), gbc)
            gbc.gridx = 1; panel.add(formatCombo, gbc)
            return panel
        }

        fun snapshotsDir(): Path? {
            val text = dirField.text.trim().ifEmpty { return null }
            return Paths.get(text).takeIf { Files.isDirectory(it) }
        }
        fun horizonWeeks(): Int = (horizonSpinner.value as Int)
        fun model(): ForecastModel = when (modelCombo.selectedItem as String) {
            "linear" -> ForecastModel.LINEAR
            "exponential" -> ForecastModel.EXPONENTIAL
            else -> ForecastModel.AUTO
        }
        fun format(): String = formatCombo.selectedItem as String
    }

    /** Read-only preview + Save As. */
    private class ForecastPreviewDialog(
        private val project: Project,
        private val body: String,
        private val format: String
    ) : DialogWrapper(project) {
        init {
            title = "Debt Forecast — Preview"
            setOKButtonText("Save As…")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val area = JTextArea(body).apply {
                isEditable = false; lineWrap = false; caretPosition = 0
                border = JBUI.Borders.empty(4)
            }
            val panel = JPanel(BorderLayout())
            panel.preferredSize = Dimension(800, 520)
            panel.add(JBScrollPane(area), BorderLayout.CENTER)
            return panel
        }

        override fun doOKAction() {
            val ext = if (format == "html") "html" else "md"
            val descriptor = FileSaverDescriptor(
                "Save Debt Forecast",
                "Choose destination",
                ext
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            val target = saver.save(baseDir, "debt-forecast.$ext")
            if (target != null) {
                try {
                    val f = target.file
                    f.parentFile?.mkdirs()
                    f.writeText(body)
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f.toPath())
                    Messages.showInfoMessage(project, "Saved to ${f.absolutePath}", "Debt Forecast")
                    super.doOKAction()
                } catch (t: Throwable) {
                    Messages.showErrorDialog(project, t.message ?: "I/O error", "Debt Forecast")
                }
            }
        }
    }
}
