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
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.util.concurrency.AppExecutorUtil
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
        presentation.text =
            LocalReviewBundle.message(
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
                        val hash =
                            ReadAction.nonBlocking<String?> {
                                if (project.isDisposed) null else t.hash()
                            }.executeSynchronously() ?: continue
                        service.mark(t.key, hash)
                    }
                } finally {
                    SafeRefresh.refreshFileStatuses(project)
                    SafeRefresh.scheduleChangesViewRefresh(project)
                }
            }
        }
    }

    private fun collectTargets(
        e: AnActionEvent,
        project: Project?,
    ): List<Target> {
        if (project == null) return emptyList()
        return TargetCollector.collect(
            project = project,
            changes = e.getData(VcsDataKeys.CHANGES),
            unversionedPaths = e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY),
            candidateFiles =
                buildList {
                    e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.let { addAll(it) }
                    e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { add(it) }
                },
        )
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

/**
 * Sentinel stored for a deletion change that the user marks as reviewed. Not a valid SHA-256,
 * so the [pl.archiprogram.localreview.state.ReviewStateService.reconcile] rehash step drops
 * the mark if the deletion is ever undone (real content → different hash → entry removed).
 */
internal const val DELETED_HASH: String = "<deleted>"

internal fun Change.hashAfter(): String? {
    val after = afterRevision
    if (after == null) {
        // Pure deletion (no afterRevision) but the VCS knew the file before: record a sentinel
        // so the mark persists for the deletion itself. A non-deletion Change with neither
        // revision is unexpected — return null.
        return if (beforeRevision != null) DELETED_HASH else null
    }
    val vf = after.file.virtualFile ?: return null
    return ContentHasher.getInstance().hash(vf)
}
