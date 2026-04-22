package pl.archiprogram.localreview.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import pl.archiprogram.localreview.LocalReviewBundle

class CounterWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID
    override fun getDisplayName(): String = LocalReviewBundle.message("widget.counter.name")

    override fun isAvailable(project: Project): Boolean {
        if (project.isDisposed) return false
        return ProjectLevelVcsManager.getInstance(project).hasActiveVcss()
    }

    override fun createWidget(project: Project): StatusBarWidget = CounterWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        com.intellij.openapi.util.Disposer.dispose(widget)
    }

    companion object {
        const val WIDGET_ID = "pl.archiprogram.localreview.counter"
    }
}
