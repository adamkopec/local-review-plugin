package pl.archiprogram.localreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.messages.MessageBusConnection
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.vcs.BranchProvider
import pl.archiprogram.localreview.vcs.ReviewBreakdown

class CounterWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private var connection: MessageBusConnection? = null

    override fun ID(): String = CounterWidgetFactory.WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        val conn = project.messageBus.connect(this)
        conn.subscribe(
            ReviewStateService.TOPIC,
            object : ReviewStateService.Listener {
                override fun stateChanged() = repaint()
            },
        )
        conn.subscribe(
            ChangeListListener.TOPIC,
            object : ChangeListListener {
                override fun changeListUpdateDone() = repaint()
            },
        )
        connection = conn
        repaint()
    }

    override fun dispose() {
        connection?.disconnect()
        connection = null
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val breakdown = ReviewBreakdown.compute(project)
        if (breakdown.totalCount == 0) return LocalReviewBundle.message("widget.counter.empty")
        return "Reviewed ${breakdown.viewedCount}/${breakdown.totalCount}"
    }

    override fun getAlignment(): Float = 0.0f

    override fun getTooltipText(): String {
        val breakdown = ReviewBreakdown.compute(project)
        if (breakdown.totalCount == 0) return LocalReviewBundle.message("widget.counter.empty.tooltip")
        val branch = primaryBranch()
        return if (branch != null && branch != Key.NO_BRANCH && branch != Key.NO_VCS) {
            LocalReviewBundle.message("widget.counter.tooltip", breakdown.viewedCount, breakdown.totalCount, branch)
        } else {
            LocalReviewBundle.message("widget.counter.tooltip.noBranch", breakdown.viewedCount, breakdown.totalCount)
        }
    }

    private fun primaryBranch(): String? {
        val mgr = com.intellij.openapi.vcs.ProjectLevelVcsManager.getInstance(project)
        val roots = mgr.allVcsRoots
        if (roots.isEmpty()) return null
        val provider = BranchProvider.getInstance()
        return roots.firstOrNull()?.path?.let { vf -> provider.currentBranch(project, vf) }
    }

    private fun repaint() {
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }
}
