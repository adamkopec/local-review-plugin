package pl.archiprogram.localreview.vcs

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.util.concurrency.AppExecutorUtil
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.settings.LocalReviewSettings
import pl.archiprogram.localreview.state.ReviewStateService
import pl.archiprogram.localreview.ui.SafeRefresh

/**
 * Reconciles the review state against the [ChangeListManager] after every update:
 *  - drops entries whose file is no longer a Change (committed, reverted, deleted outside VCS)
 *  - re-keys entries on rename ([Change.beforeRevision] != [Change.afterRevision])
 *  - re-hashes as defense-in-depth against missed VFS events
 *
 * Skips auto-invalidation for files in [FileStatus.MERGED_WITH_CONFLICTS]; those are handled
 * through the normal edit → invalidate path once the user resolves the conflict.
 */
class ChangeSetListener(private val project: Project) : ChangeListListener {
    override fun changeListUpdateDone() {
        AppExecutorUtil.getAppExecutorService().submit { reconcile() }
    }

    private fun reconcile() {
        if (project.isDisposed) return
        val service = project.service<ReviewStateService>()
        val clm = ChangeListManager.getInstance(project)
        val hasher = ContentHasher.getInstance()

        val result =
            try {
                ReadAction.nonBlocking<ChangeSetScanner.Result> {
                    ChangeSetScanner.scan(
                        project = project,
                        changes = clm.allChanges,
                        unversionedPaths = clm.unversionedFilesPaths,
                        isViewed = service::isViewed,
                        hasher = hasher,
                    )
                }.executeSynchronously()
            } catch (e: Exception) {
                LOG.warn("Reconcile failed: ${e.message}", e)
                return
            }

        val settings = LocalReviewSettings.getInstance().current()
        val stateChanged =
            service.reconcile(
                currentChanges = result.currentChanges,
                renames = result.renames,
                rehashedContent = result.rehash,
                settings = settings,
            )
        // CRITICAL: only refresh when state actually changed. Calling SafeRefresh on every
        // CLM update would create a feedback loop (refresh → CLM update → reconcile → refresh).
        if (stateChanged) {
            SafeRefresh.refreshFileStatuses(project)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ChangeSetListener::class.java)
    }
}
