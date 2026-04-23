package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import pl.archiprogram.localreview.hash.ContentHasher
import pl.archiprogram.localreview.state.Key

/**
 * Pure transformation from a [Change] list + unversioned [FilePath] list into the three inputs
 * that `ReviewStateService.reconcile` needs: the current-key set, the rename map, and the
 * defense-in-depth rehash map. Split out of [ChangeSetListener] so it can be unit-tested without
 * a real `ChangeListManager` / `ReadAction` context.
 *
 * Unversioned files are part of the Commit view's changeset (see [ReviewBreakdown] and
 * [pl.archiprogram.localreview.action.TargetCollector]), so their keys must appear in
 * [Result.currentChanges] — otherwise `reconcile` drops their viewed marks on the first CLM
 * update after the user hits "Mark as Reviewed".
 *
 * Skips rehash for files in [FileStatus.MERGED_WITH_CONFLICTS] — their contents drift by
 * design while the user resolves the conflict.
 */
internal object ChangeSetScanner {

    data class Result(
        val currentChanges: Set<Key>,
        val renames: Map<Key, Key>,
        val rehash: Map<Key, String>,
    )

    fun scan(
        project: Project,
        changes: Collection<Change>,
        unversionedPaths: Collection<FilePath>,
        isViewed: (Key) -> Boolean,
        hasher: ContentHasher,
    ): Result {
        val currentChanges = mutableSetOf<Key>()
        val renames = mutableMapOf<Key, Key>()
        val rehash = mutableMapOf<Key, String>()

        for (change in changes) {
            val skipRehash = change.fileStatus == FileStatus.MERGED_WITH_CONFLICTS
            val after = change.afterRevision?.file
            val before = change.beforeRevision?.file

            val afterKey = after?.let { KeyDeriver.keyFor(project, it) }
            val beforeKey = before?.let { KeyDeriver.keyFor(project, it) }

            val activeKey = afterKey ?: beforeKey
            if (activeKey != null) currentChanges.add(activeKey)

            if (afterKey != null && beforeKey != null && afterKey != beforeKey) {
                renames[beforeKey] = afterKey
            }

            if (!skipRehash && afterKey != null) {
                val vf = after?.virtualFile
                if (vf != null && (isViewed(afterKey) || renames.containsValue(afterKey))) {
                    val hex = hasher.hash(vf)
                    if (hex != null) rehash[afterKey] = hex
                }
            }
        }

        for (fp in unversionedPaths) {
            val key = KeyDeriver.keyFor(project, fp) ?: continue
            currentChanges.add(key)

            val vf = fp.virtualFile ?: continue
            if (isViewed(key)) {
                val hex = hasher.hash(vf)
                if (hex != null) rehash[key] = hex
            }
        }

        return Result(currentChanges, renames, rehash)
    }
}
