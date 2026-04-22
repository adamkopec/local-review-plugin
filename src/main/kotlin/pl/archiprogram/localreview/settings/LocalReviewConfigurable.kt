package pl.archiprogram.localreview.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.mcp.isMcpServerPluginAvailable
import javax.swing.JComponent

class LocalReviewConfigurable : Configurable {

    private val settings = LocalReviewSettings.getInstance()
    private val model = LocalReviewSettings.State().apply {
        copyFrom(settings.current())
    }

    private var panel: JComponent? = null
    private var groupingCheckBox: JBCheckBox? = null
    private var debugLoggingCheckBox: JBCheckBox? = null
    private var mcpToolsCheckBox: JBCheckBox? = null

    override fun getDisplayName(): String = LocalReviewBundle.message("settings.title")

    override fun createComponent(): JComponent {
        // Capture once at dialog open — the MCP plugin can't be installed without an IDE restart
        // on the IDE versions we target, so the value is stable for the dialog's lifetime.
        val mcpPresent = isMcpServerPluginAvailable()
        return panel {
            group(LocalReviewBundle.message("settings.group.behavior")) {
                row(LocalReviewBundle.message("settings.ttlDays")) {
                    intTextField(range = 0..3650)
                        .bindIntText(model::ttlDays)
                }
                row(LocalReviewBundle.message("settings.perBranchCap")) {
                    intTextField(range = 0..100_000)
                        .bindIntText(model::perBranchCap)
                }
                row {
                    groupingCheckBox = checkBox(LocalReviewBundle.message("settings.enableGrouping"))
                        .bindSelected(model::enableGrouping).component
                }
                row {
                    debugLoggingCheckBox = checkBox(LocalReviewBundle.message("settings.enableDebugLogging"))
                        .bindSelected(model::enableDebugLogging).component
                }
                row {
                    mcpToolsCheckBox = checkBox(LocalReviewBundle.message("settings.enableMcpTools"))
                        .bindSelected(model::enableMcpTools)
                        .comment(
                            if (mcpPresent) LocalReviewBundle.message("settings.enableMcpTools.comment")
                            else LocalReviewBundle.message("settings.enableMcpTools.missing"),
                        )
                        .enabled(mcpPresent)
                        .component
                }
            }
        }.also { panel = it }
    }

    override fun isModified(): Boolean = model != settings.current()

    override fun apply() {
        settings.loadState(model.copy())
    }

    override fun reset() {
        model.copyFrom(settings.current())
    }

    private fun LocalReviewSettings.State.copyFrom(other: LocalReviewSettings.State) {
        ttlDays = other.ttlDays
        perBranchCap = other.perBranchCap
        enableGrouping = other.enableGrouping
        enableDebugLogging = other.enableDebugLogging
        enableMcpTools = other.enableMcpTools
    }
}
