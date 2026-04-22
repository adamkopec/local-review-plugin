package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import pl.archiprogram.localreview.LocalReviewBundle

class CounterWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID
    override fun getDisplayName(): String = LocalReviewBundle.message("widget.counter.name")

    // Gating on ProjectLevelVcsManager.hasActiveVcss() races with StatusBarWidgetsManager init:
    // the manager populates widgets before VCS mappings settle, returns false, and since the
    // manager only re-evaluates on StatusBarWidgetFactory EP add/remove (not VCS changes),
    // the widget never revives for the session. Keep this unconditional and let getText()
    // handle empty state.
    override fun isAvailable(project: Project): Boolean = !project.isDisposed

    override fun createWidget(project: Project): StatusBarWidget = CounterWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        com.intellij.openapi.util.Disposer.dispose(widget)
    }

    companion object {
        const val WIDGET_ID = "pl.archiprogram.localreview.counter"
    }
}
