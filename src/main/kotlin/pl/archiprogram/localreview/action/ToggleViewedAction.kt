package pl.archiprogram.localreview.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.vcsUtil.VcsUtil
import pl.archiprogram.localreview.Icons
import pl.archiprogram.localreview.LocalReviewBundle
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.state.Key
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.SafeRefresh
import pl.archiprogram.localreview.vcs.KeyDeriver

class ToggleViewedAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val targets = collectTargets(e, project)
        val presentation = e.presentation

        if (project == null || targets.isEmpty()) {
            presentation.isEnabledAndVisible = true
            presentation.isEnabled = false
            presentation.text = LocalReviewBundle.message("action.toggleViewed.text")
            presentation.description = LocalReviewBundle.message("action.toggleViewed.disabled.noSelection")
            presentation.icon = Icons.UNVIEWED
            return
        }

        val hasConflict = targets.any { it.fileStatus == FileStatus.MERGED_WITH_CONFLICTS }
        if (hasConflict) {
            presentation.isEnabled = false
            presentation.text = LocalReviewBundle.message("action.toggleViewed.text")
            presentation.description = LocalReviewBundle.message("action.toggleViewed.disabled.conflict")
            presentation.icon = Icons.UNVIEWED
            return
        }

        val service = ReviewStateService.getInstance(project)
        val viewedCount = targets.count { service.isViewed(it.key) }
        val allViewed = viewedCount == targets.size

        presentation.isEnabled = true
        presentation.text = LocalReviewBundle.message(
            if (allViewed) "action.toggleViewed.text.unmark" else "action.toggleViewed.text",
        )
        presentation.description = LocalReviewBundle.message("action.toggleViewed.description")
        presentation.icon = if (allViewed) Icons.VIEWED else Icons.UNVIEWED
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val targets = collectTargets(e, project)
        if (targets.isEmpty()) return

        val service = project.service<ReviewStateService>()
        val allViewed = targets.all { service.isViewed(it.key) }

        if (allViewed) {
            for (t in targets) service.unmark(t.key)
            SafeRefresh.refreshFileStatuses(project)
            SafeRefresh.scheduleChangesViewRefresh(project)
        } else {
            AppExecutorUtil.getAppExecutorService().submit {
                try {
                    for (t in targets) {
                        if (service.isViewed(t.key)) continue
                        val hash = ReadAction.compute<String?, RuntimeException> {
                            if (project.isDisposed) null else t.hash()
                        } ?: continue
                        service.mark(t.key, hash)
                    }
                } finally {
                    SafeRefresh.refreshFileStatuses(project)
                    SafeRefresh.scheduleChangesViewRefresh(project)
                }
            }
        }
    }

    private fun collectTargets(e: AnActionEvent, project: Project?): List<Target> {
        if (project == null) return emptyList()
        val clm = ChangeListManager.getInstance(project)
        val out = mutableListOf<Target>()
        val seenKeys = mutableSetOf<Key>()

        e.getData(VcsDataKeys.CHANGES)?.forEach { change ->
            val key = change.key(project) ?: return@forEach
            if (seenKeys.add(key)) out += Target.Changed(project, change, key)
        }

        // Unversioned files from the platform's own Unversioned Files group.
        e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY)?.forEach { filePath ->
            val key = KeyDeriver.keyFor(project, filePath) ?: return@forEach
            if (seenKeys.add(key)) out += Target.Unversioned(project, filePath, key)
        }

        // Fallback: any VirtualFile selection (editor tab, editor, Project View, our synthetic
        // groups). We only accept files that are actually changes or unversioned — marking a
        // committed/unchanged file as reviewed is a no-op (the reconcile pass would drop it).
        val candidateFiles = buildList {
            e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.let { addAll(it) }
            e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { add(it) }
        }
        for (vf in candidateFiles) {
            if (vf.isDirectory || !vf.isValid) continue
            val change = clm.getChange(vf)
            val unversioned = clm.isUnversioned(vf)
            if (change == null && !unversioned) continue
            val filePath = VcsUtil.getFilePath(vf)
            val key = KeyDeriver.keyFor(project, filePath) ?: continue
            if (!seenKeys.add(key)) continue
            out += if (change != null) Target.Changed(project, change, key)
                   else Target.Unversioned(project, filePath, key)
        }

        return out
    }
}

internal sealed class Target {
    abstract val key: Key
    abstract val fileStatus: FileStatus
    abstract fun hash(): String?

    class Changed(private val project: Project, private val change: Change, override val key: Key) : Target() {
        override val fileStatus: FileStatus get() = change.fileStatus
        override fun hash(): String? = change.hashAfter()
    }

    class Unversioned(private val project: Project, private val filePath: FilePath, override val key: Key) : Target() {
        override val fileStatus: FileStatus = FileStatus.UNKNOWN
        override fun hash(): String? {
            val vf = filePath.virtualFile ?: return null
            return ContentHasher.getInstance().hash(vf)
        }
    }
}

/** Shared helpers also used by MarkAllViewedAction. */
internal fun Change.key(project: com.intellij.openapi.project.Project): Key? {
    val file = afterRevision?.file ?: beforeRevision?.file ?: return null
    return KeyDeriver.keyFor(project, file)
}

internal fun Change.hashAfter(): String? {
    val vf = afterRevision?.file?.virtualFile ?: return null
    return ContentHasher.getInstance().hash(vf)
}
