package com.jakubjirak.debtaccountant.digest

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jakubjirak.debtaccountant.model.TechDebtItem
import com.jakubjirak.debtaccountant.sla.SlaEvaluator
import com.jakubjirak.debtaccountant.sla.SlaPolicy
import com.jakubjirak.debtaccountant.store.DebtInventoryStore
import com.jakubjirak.debtaccountant.valuation.DollarValuator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Paths
import java.time.Instant
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingConstants

/**
 * IntelliJ action exposed under `Tools → Weekly Digest…`. Presents a small
 * dialog to pick a [DigestPeriod] and output format, then generates the
 * digest off the EDT via `Task.Backgroundable` and shows the rendered
 * Markdown/HTML source in a read-only preview with Copy / Save As controls.
 */
class GenerateWeeklyDigestAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = DigestOptionsDialog(project)
        if (!dialog.showAndGet()) return
        val period = dialog.selectedPeriod()
        val format = dialog.selectedFormat()

        val store = DebtInventoryStore.getInstance(project)
        val items = store.getItems()
        val previous = dialog.previousSnapshot()

        object : Task.Backgroundable(project, "Generating weekly digest…", false) {
            private var rendered: String = ""
            private var data: DigestData? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val valuator = DollarValuator()
                val now = System.currentTimeMillis()
                val collector = DigestDataCollector(
                    valuator = { valuator.valuate(it).estimatedFixCost },
                    monthlyInterestValuator = { valuator.valuate(it).monthlyInterest },
                    slaProvider = { item: TechDebtItem ->
                        val eval = SlaEvaluator.evaluate(listOf(item), SlaPolicy.DEFAULT, Instant.ofEpochMilli(now))
                        when {
                            eval.breached.isNotEmpty() -> "BREACHED"
                            eval.approaching.isNotEmpty() -> "APPROACHING"
                            else -> "OK"
                        }
                    }
                )
                val projectName = project.name
                data = collector.collect(items, previous, period, projectName, now)
                rendered = when (format) {
                    Format.HTML -> HtmlDigestRenderer.render(data!!)
                    Format.MARKDOWN -> MarkdownDigestRenderer.render(data!!)
                }
            }

            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    DigestPreviewDialog(project, rendered, format).show()
                }
            }
        }.queue()
    }
}

internal enum class Format { MARKDOWN, HTML }

internal class DigestOptionsDialog(project: Project) : DialogWrapper(project, true) {

    private val periodCombo = JComboBox(arrayOf("Last 7 days", "Last 14 days", "Last 30 days"))
    private val formatCombo = JComboBox(arrayOf("Markdown", "HTML"))
    private val previousField = com.intellij.ui.components.JBTextField()

    init {
        title = "Weekly Digest"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 0.0
        }
        fun row(label: String, comp: JComponent, y: Int) {
            gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0.0
            panel.add(JBLabel(label, SwingConstants.LEFT), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(comp, gbc)
        }
        row("Period:", periodCombo, 0)
        row("Format:", formatCombo, 1)
        row("Previous snapshot (optional):", previousField, 2)
        panel.preferredSize = Dimension(460, panel.preferredSize.height)
        return panel
    }

    fun selectedPeriod(): DigestPeriod = when (periodCombo.selectedIndex) {
        0 -> DigestPeriod.Last7
        1 -> DigestPeriod.Last14
        else -> DigestPeriod.Last30
    }

    fun selectedFormat(): Format = if (formatCombo.selectedIndex == 1) Format.HTML else Format.MARKDOWN

    fun previousSnapshot(): DigestSnapshot? {
        val path = previousField.text?.trim().orEmpty()
        if (path.isEmpty()) return null
        val p = Paths.get(path)
        return runCatching { DigestSnapshotStore(p.parent ?: Paths.get(".")).load(p) }.getOrNull()
    }
}

internal class DigestPreviewDialog(
    private val project: Project,
    private val rendered: String,
    private val format: Format
) : DialogWrapper(project, true) {

    init {
        title = "Weekly Digest Preview"
        setOKButtonText("Close")
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout())
        root.preferredSize = Dimension(820, 560)
        root.border = JBUI.Borders.empty(8)

        val area = JTextArea(rendered).apply {
            isEditable = false
            lineWrap = false
        }
        root.add(JBScrollPane(area), BorderLayout.CENTER)

        val buttons = JPanel()
        val copyBtn = JButton("Copy").apply {
            addActionListener {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                    java.awt.datatransfer.StringSelection(rendered), null
                )
                Messages.showInfoMessage(project, "Copied digest to clipboard.", "Weekly Digest")
            }
        }
        val saveBtn = JButton("Save As…").apply {
            addActionListener { saveAs() }
        }
        buttons.add(copyBtn)
        buttons.add(saveBtn)
        root.add(buttons, BorderLayout.SOUTH)

        return root
    }

    private fun saveAs() {
        val ext = if (format == Format.HTML) "html" else "md"
        val descriptor = FileSaverDescriptor("Save Weekly Digest", "Save the digest to a file.")
            .apply { withExtensionFilter(ext) }
        val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val baseDir = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val target = saver.save(baseDir, "weekly-digest.$ext")
        if (target != null) {
            val f = target.file
            f.parentFile?.mkdirs()
            f.writeText(rendered)
            Messages.showInfoMessage(project, "Saved to ${f.absolutePath}", "Weekly Digest")
        }
    }
}
