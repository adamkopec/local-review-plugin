package com.localreview.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.messages.MessageBusConnection
import com.localreview.LocalReviewBundle
import com.localreview.state.Key
import com.localreview.state.ReviewStateService
import com.localreview.vcs.BranchProvider
import com.localreview.vcs.KeyDeriver

class CounterWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    private var connection: MessageBusConnection? = null

    override fun ID(): String = CounterWidgetFactory.WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        val conn = project.messageBus.connect(this)
        conn.subscribe(ReviewStateService.TOPIC, object : ReviewStateService.Listener {
            override fun stateChanged() = repaint()
        })
        conn.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() = repaint()
        })
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
        val (viewed, total) = computeCounts()
        if (total == 0) return LocalReviewBundle.message("widget.counter.empty")
        return "Reviewed $viewed/$total"
    }

    override fun getAlignment(): Float = 0.0f

    override fun getTooltipText(): String {
        val (viewed, total) = computeCounts()
        if (total == 0) return LocalReviewBundle.message("widget.counter.empty")
        val branch = primaryBranch()
        return if (branch != null && branch != Key.NO_BRANCH && branch != Key.NO_VCS) {
            LocalReviewBundle.message("widget.counter.tooltip", viewed, total, branch)
        } else {
            LocalReviewBundle.message("widget.counter.tooltip.noBranch", viewed, total)
        }
    }

    private fun computeCounts(): Pair<Int, Int> {
        if (project.isDisposed) return 0 to 0
        val clm = ChangeListManager.getInstance(project)
        val service = ReviewStateService.getInstance(project)
        val changes = clm.allChanges
        val total = changes.size
        var viewed = 0
        for (change in changes) {
            val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: continue
            val key = KeyDeriver.keyFor(project, file) ?: continue
            if (service.isViewed(key)) viewed++
        }
        return viewed to total
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
