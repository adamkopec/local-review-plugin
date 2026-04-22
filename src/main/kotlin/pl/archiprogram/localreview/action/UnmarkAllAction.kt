package pl.archiprogram.localreview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.SafeRefresh

class UnmarkAllAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        presentation.text = LocalReviewBundle.message("action.unmarkAll.text")
        presentation.description = LocalReviewBundle.message("action.unmarkAll.description")
        presentation.isEnabled = project != null &&
            ReviewStateService.getInstance(project).size() > 0
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<ReviewStateService>().clearAll()
        SafeRefresh.refreshFileStatuses(project)
        SafeRefresh.scheduleChangesViewRefresh(project)
    }
}
