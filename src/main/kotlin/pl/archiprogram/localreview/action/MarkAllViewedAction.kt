package pl.archiprogram.localreview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.concurrency.AppExecutorUtil
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.SafeRefresh

class MarkAllViewedAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val changes = selectedChanges(e)
        val presentation = e.presentation
        presentation.text = LocalReviewBundle.message("action.markAllViewed.text")
        presentation.description = LocalReviewBundle.message("action.markAllViewed.description")
        presentation.isEnabled = project != null && changes.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val changes = selectedChanges(e).filter { it.fileStatus != FileStatus.MERGED_WITH_CONFLICTS }
        if (changes.isEmpty()) return
        val service = project.service<ReviewStateService>()

        AppExecutorUtil.getAppExecutorService().submit {
            try {
                for (change in changes) {
                    val key = change.key(project) ?: continue
                    if (service.isViewed(key)) continue
                    val hash =
                        runReadAction {
                            if (project.isDisposed) null else change.hashAfter()
                        } ?: continue
                    service.mark(key, hash)
                }
            } finally {
                SafeRefresh.refreshFileStatuses(project)
                SafeRefresh.scheduleChangesViewRefresh(project)
            }
        }
    }

    /** Prefer the selected changelists when the user has an explicit changelist selection; else
     *  fall back to the selected changes; else all changes. */
    private fun selectedChanges(e: AnActionEvent): List<Change> {
        val selectedChangeLists = e.getData(VcsDataKeys.CHANGE_LISTS)
        if (!selectedChangeLists.isNullOrEmpty()) {
            return selectedChangeLists.flatMap { it.changes }
        }
        val selected = e.getData(VcsDataKeys.CHANGES)
        if (!selected.isNullOrEmpty()) return selected.toList()
        return emptyList()
    }
}
