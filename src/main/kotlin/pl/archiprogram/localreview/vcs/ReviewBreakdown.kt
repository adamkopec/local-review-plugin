package pl.archiprogram.localreview.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import pl.archiprogram.localreview.state.ReviewStateService

/**
 * Single source of truth for what is viewed vs. unviewed in the current project. Both the
 * "Reviewed N of M" tool-window group and the status bar counter derive their numbers from
 * here — so the two surfaces cannot drift out of sync.
 *
 * Includes unversioned files alongside tracked changes: the commit view lists them and the
 * user can mark them as viewed, so they belong in the total.
 */
data class ReviewBreakdown(
    val viewedChanges: List<Change>,
    val unviewedChanges: List<Change>,
    val viewedUnversioned: List<VirtualFile>,
    val unviewedUnversioned: List<VirtualFile>,
) {
    val viewedCount: Int get() = viewedChanges.size + viewedUnversioned.size
    val unviewedCount: Int get() = unviewedChanges.size + unviewedUnversioned.size
    val totalCount: Int get() = viewedCount + unviewedCount

    companion object {
        val EMPTY = ReviewBreakdown(emptyList(), emptyList(), emptyList(), emptyList())

        fun compute(project: Project): ReviewBreakdown {
            if (project.isDisposed) return EMPTY
            val clm = ChangeListManager.getInstance(project)
            val service = ReviewStateService.getInstance(project)

            val viewedChanges = mutableListOf<Change>()
            val unviewedChanges = mutableListOf<Change>()
            for (change in clm.allChanges) {
                val file = change.afterRevision?.file ?: change.beforeRevision?.file ?: continue
                val key = KeyDeriver.keyFor(project, file) ?: continue
                if (service.isViewed(key)) viewedChanges += change else unviewedChanges += change
            }

            val viewedUnversioned = mutableListOf<VirtualFile>()
            val unviewedUnversioned = mutableListOf<VirtualFile>()
            for (filePath in clm.unversionedFilesPaths) {
                val vf = filePath.virtualFile ?: continue
                val key = KeyDeriver.keyFor(project, filePath) ?: continue
                if (service.isViewed(key)) viewedUnversioned += vf else unviewedUnversioned += vf
            }

            return ReviewBreakdown(
                viewedChanges = viewedChanges,
                unviewedChanges = unviewedChanges,
                viewedUnversioned = viewedUnversioned,
                unviewedUnversioned = unviewedUnversioned,
            )
        }
    }
}
