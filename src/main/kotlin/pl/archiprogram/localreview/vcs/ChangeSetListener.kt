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
import pl.archiprogram.localreview.state.Key
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

        val currentChanges = mutableSetOf<Key>()
        val renames = mutableMapOf<Key, Key>()
        val rehash = mutableMapOf<Key, String>()

        try {
            ReadAction.run<RuntimeException> {
                val changes = clm.allChanges
                val hasher = ContentHasher.getInstance()
                for (change in changes) {
                    val skipRehash = change.fileStatus == FileStatus.MERGED_WITH_CONFLICTS
                    val after = change.afterRevision?.file
                    val before = change.beforeRevision?.file

                    val afterKey = after?.let { KeyDeriver.keyFor(project, it) }
                    val beforeKey = before?.let { KeyDeriver.keyFor(project, it) }

                    val activeKey = afterKey ?: beforeKey
                    if (activeKey != null) {
                        currentChanges.add(activeKey)
                    }

                    // Rename: both sides present, different paths
                    if (afterKey != null && beforeKey != null && afterKey != beforeKey) {
                        renames[beforeKey] = afterKey
                    }

                    // Defense-in-depth rehash — only for live files we haven't skipped
                    if (!skipRehash && afterKey != null) {
                        val vf = after?.virtualFile
                        if (vf != null && (service.isViewed(afterKey) || renames.containsValue(afterKey))) {
                            val hex = hasher.hash(vf)
                            if (hex != null) rehash[afterKey] = hex
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Reconcile failed: ${e.message}", e)
            return
        }

        val settings = LocalReviewSettings.getInstance().current()
        val stateChanged = service.reconcile(
            currentChanges = currentChanges,
            renames = renames,
            rehashedContent = rehash,
            settings = settings,
        )
        // CRITICAL: only refresh when state actually changed. Calling SafeRefresh on every
        // CLM update would create a feedback loop (refresh → CLM update → reconcile → refresh).
        // Even when state did change, skip the tree refresh — our grouping policy re-reads
        // state on the next natural tree rebuild, and widget observers get the messageBus event.
        if (stateChanged) {
            SafeRefresh.refreshFileStatuses(project)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ChangeSetListener::class.java)
    }
}
